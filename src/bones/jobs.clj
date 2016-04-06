(ns bones.jobs
  " given a symbol and config, build all components of an onyx job
  with three tasks kafka-input->function->kafka-output
  (def x.y/fn [s] (str s \"-yo\"))
  (api/submit-jobs (bones.jobs/build-jobs {} [:x.y/fn]))
  (kafka/produce \"x.y..fn-input\" \"hello\")
  (kafka/consume \"x.y..fn-output\") => \"hello-yo\"
  "
  (:require [clojure.core.async :as a]))

(defn topic-reader [^String topic]
  "builds a catalog entry that reads from a kafka topic"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/read-messages
   :onyx/batch-size 1
   :onyx/min-peers 1 ;;?
   :onyx/max-peers 1 ;;?
   :onyx/type :input
   :onyx/medium :kafka
   :kafka/group-id "onyx" ;;?
   :kafka/topic topic
   :kafka/offset-reset :largest
   :kafka/zookeeper "127.0.0.1:2181" ;; can be updated in conf
   :kafka/deserializer-fn :bones.serializer/deserialize}) ;; can be updated in conf

(defn topic-writer [^String topic]
  "builds a catalog entry that writes to a kafka topic"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/write-messages
   :onyx/batch-size 1
   :onyx/min-peers 1 ;;?
   :onyx/max-peers 1 ;;?
   :onyx/type :output
   :onyx/medium :kafka
   :kafka/group-id "onyx" ;;?
   :kafka/topic topic
   :kafka/offset-reset :largest
   :kafka/zookeeper "127.0.0.1:2181" ;; can be updated in conf
   :kafka/serializer-fn :bones.serializer/serialize}) ;; can be updated in conf

(defn topic-function [^clojure.lang.Keyword ns-fn]
  "builds a catalog entry that performs some user function"
  {:onyx/name ns-fn
   :onyx/fn ns-fn
   :onyx/batch-size 1
   :onyx/max-peers 1 ;;testing peer configuration
   :onyx/type :function})

(defn redis-publisher []
  {:onyx/name :redis-publisher
   :onyx/plugin :onyx.plugin.redis/writer
   :onyx/type :output
   :onyx/medium :redis
   :redis/uri "redis://127.0.0.1:6379"
   :onyx/batch-size 1})

(defn core-async-writer [^String topic]
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.core-async/output
   :onyx/type :output
   :onyx/medium :core.async
   :onyx/batch-size 1
   :onyx/max-peers 1
   :onyx/doc "Writes segments to a core.async channel"})

;; todo refactor use map
(defn kafka-lifecycle [input-task output-task]
  [{:lifecycle/task input-task
    :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}
   {:lifecycle/task output-task
    :lifecycle/calls :onyx.plugin.kafka/write-messages-calls}])

(defn sym-to-topic [^clojure.lang.Keyword job-sym]
  (-> (str job-sym)
      (clojure.string/replace "/" "..") ;; / is illegal in kafka topic name
      (subs 1))) ;; remove leading colon

(defn topic-to-sym [^String topic-name]
  (-> topic-name
      (clojure.string/replace ".." "/") ;; puts the / back
      (keyword))) ;; put the colon back

(defn topic-name-input [^clojure.lang.Keyword job-sym]
  (str (sym-to-topic job-sym) "-input"))

(defn topic-name-output [^clojure.lang.Keyword job-sym]
  (str (sym-to-topic job-sym) "-output"))

(defn ns-name-output [^clojure.lang.Keyword job-sym]
  (str (namespace job-sym ) "-output"))

(defn task-name-results [^clojure.lang.Keyword job-sym]
  (subs (str job-sym "-results") 1))

(defn catalog [job-sym]
  [(topic-reader (topic-name-input job-sym))
   (topic-function job-sym)
   (redis-publisher)
   (topic-writer (ns-name-output job-sym))
   ])

(defn workflow [job-sym]
  "routes segments through a function in a kafka-function-kafka sandwich
   given a symbol x, create two vectors as: [[x-input x] [x x-output]]"
  (let [input (keyword (topic-name-input job-sym))
        output (keyword (ns-name-output job-sym))]
    [[input job-sym]
     [job-sym :redis-publisher]
     [job-sym output]
     ]))

(defn lifecycle [job-sym]
  (kafka-lifecycle (keyword (topic-name-input job-sym))
                   (keyword (ns-name-output job-sym))
                   ))

(def constantly-true (constantly true))

(defn background-message?
  "the function returned a :_background_message key that was intercepted,
  and placed on :message for kafka, and it's value has :fn and :args keys"
  [event old-segment new-segment all-new]
  (let [{:keys [fn args] }  (:message new-segment)]
    (and fn args)))

(defn results-present?
  "this is a wall to stop the flow of data. A background job can have any output, and
  it may not be desirable to have anything output. An Onyx function task requires an output though.
  With this test the flow can come to a stop. Or it can be used someother way (testing?)"
  [event old-segment new-segment all-new]
  (contains? new-segment :_results))

(defn flow-conditions [job-sym]
  [{:flow/from job-sym
    :flow/to [(keyword (ns-name-output job-sym))]
    :flow/predicate [::background-message?]
    :flow/doc "Emits segment to kafka for background processing if :_background given"}
   {:flow/from job-sym
    :flow/to [:redis-publisher]
    :flow/predicate ::constantly-true
    :flow/doc "Always send result of command to user through redis."}
   ])

(defn results-condition [job-sym]
  {:flow/from job-sym
   :flow/to [(keyword (task-name-results job-sym))]
   :flow/predicate [::results-present?]
   :flow/doc "Emits segment to core-async channel if :_results present"})

(defn command-job [job-sym]
  {:workflow (workflow job-sym)
   :catalog (catalog job-sym)
   :lifecycles (lifecycle job-sym)
   :flow-conditions (flow-conditions job-sym)
   :task-scheduler :onyx.task-scheduler/balanced})

(def results-chan
  "a receiver to satisfy :onyx/fn's need for an output, opt-in usage,
you'll have to create another task to use this I think"
  (a/chan (a/dropping-buffer 100)))

(defn inject-results-ch [event lifecycle]
  {:core.async/chan results-chan})

(def writer-calls
  {:lifecycle/before-task-start inject-results-ch})

(defn background-job [job-sym]
  ;; input and output are kind of reversed here
  ;; reader <- output for example
  (let [reader-str (ns-name-output job-sym)
        input-task (keyword reader-str)
        writer-str (task-name-results job-sym)]
    {:workflow [[input-task job-sym]
                [job-sym (keyword writer-str)]]
     :catalog [(topic-reader reader-str)
               (topic-function job-sym)
               (core-async-writer writer-str)]
     :lifecycles [{:lifecycle/task input-task
                   :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}
                  {:lifecycle/task (keyword writer-str)
                   :lifecycle/calls ::writer-calls}
                  {:lifecycle/task (keyword writer-str)
                   :lifecycle/calls :onyx.plugin.core-async/writer-calls}]
     :flow-conditions [(results-condition job-sym)]
     :task-scheduler :onyx.task-scheduler/balanced}))

(defn configure-job [conf job]
  (cond-> job
    (:zookeeper/address conf) (->
                               (assoc-in [:catalog 0 :kafka/zookeeper]
                                         (:zookeeper/address conf)))
    ;;these < 3 checks are really command-job? (and not background-job?) checks
    (and (:zookeeper/address conf)
         (< 3 (count (:catalog job)))) (->
                                        (assoc-in [:catalog 3 :kafka/zookeeper]
                                                  (:zookeeper/address conf)))
    (:kafka/deserializer-fn conf) (assoc-in [:catalog 0 :kafka/deserializer-fn]
                                            (:kafka/deserializer-fn conf))
    ;;fixme this 0 3 stuff needs to change
    (and (:kafka/serializer-fn conf)
         (< 3 (count (:catalog job)))) (->
                                         (assoc-in [:catalog 3 :kafka/serializer-fn]
                                                   (:kafka/serializer-fn conf)))
    (:onyx.task-scheduler conf) (assoc :task-scheduler
                                       (:onyx.task-scheduler conf))))

(defn build-background-job [conf job-sym]
  "here we combine the configurable bits with the built bits"
  (configure-job conf (background-job job-sym)))

;; todo look into using the traversy library here
(defn build-configured-job [conf job-sym]
  "here we combine the configurable bits with the built bits"
  (configure-job conf (command-job job-sym)))

(defn build-background-jobs [conf jobs]
  (mapv (partial build-background-job conf) jobs))

(defn build-jobs [conf jobs]
  (mapv (partial build-configured-job conf) jobs))

(defmacro namespaced-symbol [name]
  `(keyword (str *ns*) (str (quote ~name))))

(defn job-middleware [job-sym job-fn]
  "wraps input and output of function in an appropriate message for onyx-redis"
  (fn [segment]
    (let [;; coming from kafka
          kafka-key (:_kafka-key segment)
          uuid (:uuid segment)
          incoming-message (:message segment)
          ;; this is the fn call
          _output (job-fn incoming-message)
          ;; maybe going to kafka
          background-message (:_background _output)
          ;; definitely going to redis
          output (dissoc _output :_background)
          redis-key (str "jobs-output-" kafka-key)
          output-message {:command job-sym :job-sym job-sym :uuid uuid :output output :input incoming-message}
          ]

      ;; always go to redis, only go to kafka if background-message
      {:op :publish
       :args [redis-key output-message]

       :message background-message
       :key kafka-key
       }
      )))

(defmacro defjob [name signature & form]
  `(def ~name
     (job-middleware (namespaced-symbol ~name)
      (fn ~signature
        ~@form
        ))))

(ns bones.jobs
  " given a symbol and config, build all components of an onyx job
  with three tasks kafka-input->function->kafka-output
  (def x.y/fn [s] (str s \"-yo\"))
  (api/submit-jobs (bones.jobs/build-jobs {} [:x.y/fn]))
  (kafka/produce \"x.y..fn-input\" \"hello\")
  (kafka/consume \"x.y..fn-output\") => \"hello-yo\"
  ")

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
   :onyx/type :function})

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

(defn catalog [job-sym]
  [(topic-reader (topic-name-input job-sym))
   (topic-function job-sym)
   (topic-writer (topic-name-output job-sym))])

(defn workflow [job-sym]
  "routes segments through a function in a kafka-function-kafka sandwich
   given a symbol x, create two vectors as: [[x-input x] [x x-output]]"
  (let [input (topic-name-input job-sym)
        output (topic-name-output job-sym)
        first-flow (mapv keyword [input job-sym])
        second-flow (mapv keyword [job-sym output])]
    [first-flow
     second-flow]))

(defn lifecycle [job-sym]
  (kafka-lifecycle (keyword (topic-name-input job-sym))
                   (keyword (topic-name-output job-sym))))

(defn build-default-job [job-sym]
  {:workflow (workflow job-sym)
   :catalog (catalog job-sym)
   :lifecycles (lifecycle job-sym)
   :task-scheduler :onyx.task-scheduler/balanced})

(defn build-configured-job [conf job-sym]
  "here we combine the configurable bits with the built bits"
  (let [job (build-default-job job-sym)]
    (cond-> job
      (:zookeeper/address conf) (->
                                 (assoc-in [:catalog 0 :kafka/zookeeper]
                                           (:zookeeper/address conf))
                                 (assoc-in [:catalog 2 :kafka/zookeeper]
                                           (:zookeeper/address conf)))
      (:kafka/deserializer-fn conf) (assoc-in [:catalog 0 :kafka/deserializer-fn]
                                              (:kafka/deserializer-fn conf))
      (:kafka/serializer-fn conf) (assoc-in [:catalog 2 :kafka/serializer-fn]
                                            (:kafka/serializer-fn conf))
      (:onyx.task-scheduler conf) (assoc :task-scheduler
                                         (:onyx.task-scheduler conf)))))

(defn build-jobs [conf jobs]
  (mapv (partial build-configured-job conf) jobs))

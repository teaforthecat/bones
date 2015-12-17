(ns bones.jobs
  (:require [onyx.api]))

(defn topic-reader [^String topic conf]
  "builds a task that reads from a kafka topic; for the catalog"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/read-messages
   :onyx/batch-size 1
   :onyx/type :input
   :onyx/medium :kafka
   :kafka/topic topic ;; namespaced function reference for uniqueness and clarity
   :kafka/zookeeper (or (:zookeeper/address conf) "127.0.0.1:2181")
   :kafka/deserializer-fn :bones.serializer/deserializer})

(defn topic-writer [^String topic conf]
  "builds a task that writes to a kafka topic; for the catalog"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/write-messages
   :onyx/batch-size 1
   :onyx/type :output
   :onyx/medium :kafka
   :kafka/topic topic ;; namespaced function reference for uniqueness and clarity
   :kafka/zookeeper (or (:zookeeper/address conf) "127.0.0.1:2181")
   :kafka/serializer-fn :bones.serializer/serializer})

(defn topic-function [fn-sym conf]
  {:onyx/name fn-sym
   :onyx/fn fn-sym
   :onyx/batch-size 1
   :onyx/type :function})

(defn kafka-lifecycle [input-task output-task]
  [{:lifecycle/task input-task
    :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}
   {:lifecycle/task output-task
    :lifecycle/calls :onyx.plugin.kafka/write-messages-calls}])

(defn sym-to-topic [job-sym]
  (-> (str job-sym)
      (clojure.string/replace "/" "..") ;; / is illegal in kafka topic name
      (subs 1))) ;; remove leading colon

(defn topic-to-sym [topic-name]
  (-> topic-name
      (clojure.string/replace ".." "/") ;; / is illegal in kafka topic name
      (keyword)))

(defn topic-name-input [job-sym]
  (str (sym-to-topic job-sym) "-input"))

(defn topic-name-output [job-sym]
  (str (sym-to-topic job-sym) "-output"))

(defn build-catalog-for-job [conf job-sym]
  [(topic-reader (topic-name-input job-sym) conf)
   (topic-function job-sym conf)
   (topic-writer (topic-name-output job-sym) conf)])

(defn build-workflow-entry [conf job-sym]
  "routes segments through a function.
   given a symbol x, create two vectors as: [[x-input x] [x x-output]]"
  (let [input (topic-name-input job-sym)
        output (topic-name-output job-sym)
        first-flow (mapv keyword [input job-sym])
        second-flow (mapv keyword [job-sym output])]
    [first-flow
     second-flow]))

(defn build-lifecycle-entries [conf job-sym]
  (kafka-lifecycle (keyword (topic-name-input job-sym))
                   (keyword (topic-name-output job-sym))))

(defn build-job [conf job-sym]
  {:workflow (build-workflow-entry conf job-sym)
   :catalog (build-catalog-for-job conf job-sym)
   :lifecycles (build-lifecycle-entries conf job-sym)
   :task-scheduler :onyx.task-scheduler/greedy})

(defn build-jobs [conf jobs]
  (mapv (partial build-job conf) jobs))

(defn submit-jobs [config jobs]
  (map
   (partial onyx.api/submit-job config)
   jobs))

(comment
  ;; user-space example:
  (defn handle-complex-command [segment]
    segment)

  (defn handle-simple-command [segment]
    segment)

  (def background-jobs
    [::handle-complex-command
     ::handle-simple-command])

  (def background-onyx-config
    {:onyx/id "123"
     :onyx/batch-size 1
     :zookeeper/address "127.0.0.1:2181"
     :onyx.messaging/impl :aeron
     :onyx.peer/job-scheduler :onyx.job-scheduler/balanced
     :zookeeper.server/port 2181
     :onyx.messaging/peer-port 40201
     :onyx.messaging/bind-addr "localhost" } )



  ;;                         :task-scheduler :onyx.task-scheduler/balanced
  (onyx.api/start-peer-group background-onyx-config)

  (submit-jobs background-onyx-config
               (build-jobs background-onyx-config background-jobs))
  ;; end user-space example.

  )

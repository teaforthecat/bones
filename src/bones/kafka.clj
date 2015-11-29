(ns bones.kafka)

(defn topic-reader [^String topic conf]
  "builds a task that reads from a kafka topic; for the catalog"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/read-messages
   :onyx/type :input
   :onyx/medium :kafka
   :kafka/topic topic ;; namespaced function reference for uniqueness and clarity
   :kafka/zookeeper (or (:zookeeper/address conf) "127.0.0.1:2181")
   :kafka/deserializer-fn :bones.serializer/deserializer})

(defn topic-writer [^String topic conf]
  "builds a task that writes to a kafka topic; for the catalog"
  {:onyx/name (keyword topic)
   :onyx/plugin :onyx.plugin.kafka/write-messages
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

(defn topic-name-input [job-sym]
  (subs (str job-sym "-input") 1))

(defn topic-name-output [job-sym]
  (subs (str job-sym "-output") 1))

(defn build-catalog-for-job [conf job-sym]
  (let [read-topic-name (subs (str job-sym "-input") 1)
        write-topic-name (subs (str job-sym "-output") 1)]
    [(topic-reader (topic-name-input job-sym) conf)
     (topic-function job-sym conf)
     (topic-writer (topic-name-output job-sym) conf)]))

(defn build-workflow-entry [job-sym]
  (let [read-topic-name (subs (str job-sym "-input") 1)
        write-topic-name (subs (str job-sym "-output") 1)]
    (mapv keyword [(topic-name-input job-sym) job-sym (topic-name-output job-sym)])))

(defn build-lifecycle-entries [job-sym]
  (kafka-lifecycle (keyword (topic-name-input job-sym))
                   (keyword (topic-name-output job-sym))))

(defn build-jobs [conf background-jobs]
  {:workflow (mapv (partial build-workflow-entry conf) background-jobs)
   :catalog (mapv (partial build-catalog-for-job conf) background-jobs)
   :lifecycles (mapv build-lifecycle-entries background-jobs)})


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

  (def background-onyx-config {})

  (submit-jobs background-onyx-config
               (build-jobs background-onyx-config background-jobs))
  ;; end user-space example.
  )

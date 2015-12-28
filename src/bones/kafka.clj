(ns bones.kafka
  (:require [bones.serializer :refer [serialize deserialize]]
            [clojure.core.async :as a]
            [byte-streams :as bs]
            [clj-kafka.core :refer [with-resource]]
            [clj-kafka.zk :as zk]
            [clj-kafka.consumer.zk :as zkc]
            [clj-kafka.new.producer :as nkp]))


; TODO move producer to system so we don't call .close on it everytime
; returns a future
(defn produce [topic key data]
  (let [bytes (serialize data)
        key-bytes (.getBytes key)
        record (nkp/record topic key-bytes bytes)
        producer-config {"bootstrap.servers" "127.0.0.1:9092"}]
    (with-open [p (nkp/producer producer-config
                                (nkp/byte-array-serializer)
                                (nkp/byte-array-serializer))]
      (nkp/send p record))))


(defn consume [topic]
  ;; imperative consumer, next make a streaming consumer
  ;; consumer must be at position 0 in bindings
  (try
    (with-resource [c (zkc/consumer {"zookeeper.connect" "127.0.0.1:2181"
                                     "group.id" "bones.kafka4"
                                     "auto.offset.reset" "smallest"
                                     "consumer.timeout.ms" "1000"})]
      zkc/shutdown
      (-> c
          (zkc/messages  topic)
          (first)
          (update :value deserialize)
          (update :key deserialize))
      ;(update (first (zkc/messages c topic)) :value deserialize)
      )
    (catch kafka.consumer.ConsumerTimeoutException e
        {:value "no messages"})))

(defn open-consumer [group-id topic]
  (let [cnsmr (zkc/consumer {"zookeeper.connect" "127.0.0.1:2181"
                              "group.id" group-id
                             "auto.offset.reset" "largest"})]
    [cnsmr (zkc/messages cnsmr topic)]))

(defn shutdown [consumer]
  (zkc/shutdown consumer))

(defn authorized? [msg group-id]
  true
  #_(and (:key msg)
       (= group-id (bs/convert (:key msg) String))))

(defn personal-consumer [chan shutdown-ch group-id topic]
  (let [cnsmr (zkc/consumer {"zookeeper.connect" "127.0.0.1:2181"
                             "group.id" group-id
                             "auto.offset.reset" "largest"})]
    (a/go (a/<! shutdown-ch) (zkc/shutdown cnsmr)) ;; easy cleanup

    (a/go
      (try
        (doseq [m (zkc/messages cnsmr topic)]
          (if (authorized? m group-id)
            (a/>! chan (deserialize (:value m)))))
       (finally
         (zkc/shutdown cnsmr) ;; incase of errors(?)
                )))))



(comment

  (def aaa (a/chan))
  (personal-consumer aaa "xyz" "bones.jobs-test..handle-simple-command-input..123")
  (def taker-ch
    (a/go
      (loop []
          (let [m (a/<! aaa)]
            (println m))
        (recur))))


  )

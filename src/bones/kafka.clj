(ns bones.kafka
  (:require [bones.serializer :refer [serialize deserialize]]
            [taoensso.timbre :as log]
            [clojure.core.async :as a]
            [byte-streams :as bs]
            [clj-kafka.core :refer [with-resource]]
            [clj-kafka.zk :as zk]
            [clj-kafka.consumer.zk :as zkc]
            [clj-kafka.new.producer :as nkp]))


; TODO move producer to system so we don't call .close on it everytime
; returns a future
(defn produce [topic key data]
  (log/info "sending " data "to kafka topic: " topic)
  (let [bytes (serialize data)
        key-bytes (.getBytes (str key))
        record (nkp/record topic key-bytes bytes)
        producer-config {"bootstrap.servers" "127.0.0.1:9092"}]
    (with-open [p (nkp/producer producer-config
                                (nkp/byte-array-serializer)
                                (nkp/byte-array-serializer))]
      (nkp/send p record))))

(defn authorized? [msg group-id]
  (and (:key msg)
       (let [m-key (str (deserialize (:key msg)))
             user-id (str group-id)]
         (= user-id m-key ))))

(defn personal-consumer [chan shutdown-ch group-id topic]
  (let [cnsmr (zkc/consumer {"zookeeper.connect" "127.0.0.1:2181"
                             "group.id" (str group-id)
                             "auto.offset.reset" "largest"})]
    (a/go (a/<! shutdown-ch) (zkc/shutdown cnsmr)) ;; easy cleanup
    (a/go
      (try
        (doseq [m (zkc/messages cnsmr topic)]
          (if (authorized? m (str group-id))
            (do
              (log/info "receiving " (deserialize (:value m))  " on " topic " from kafka ")

              (a/>! chan (deserialize (:value m))))))
       (finally
         (zkc/shutdown cnsmr) ;; incase of errors(?)
                )))))

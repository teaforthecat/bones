(ns bones.consumer-test
  (:use
        [clj-kafka.core :only (with-resource to-clojure)]
        [bones.test-utils :only (with-test-broker)])
  (:require [expectations :refer [given expect]]
            [clj-kafka.consumer.zk :as zk]
            [clj-kafka.producer :as ckp]))

(def producer-config {"metadata.broker.list" "localhost:9999"
                      "serializer.class" "kafka.serializer.DefaultEncoder"
                      "partitioner.class" "kafka.producer.DefaultPartitioner"})

(def test-broker-config {:zookeeper-port 2182
                         :kafka-port 9999
                         :topic "test"})

(defn consumer-config [] {"zookeeper.connect" "localhost:2182"
                          "group.id" (str (java.util.UUID/randomUUID))
                          "auto.offset.reset" "smallest"
                          "auto.commit.enable" "false"})

(defn string-value
  [k]
  (fn [m]
    (String. (k m) "UTF-8")))

(defn test-message
  []
  (ckp/message "test" (.getBytes "Hello, world")))

(defn send-and-receive
  [messages]
  (with-test-broker test-broker-config
    (with-resource [c (zk/consumer consumer-config)]
      zk/shutdown
      (let [p (ckp/producer producer-config)]
        (ckp/send-messages p messages)
        (first (zk/messages c "test"))))))

(given (send-and-receive [(test-message)])
       (expect :topic "test"
               :offset 0
               :partition 0
               (string-value :value) "Hello, world"))


(comment
(def consumer (zk/consumer (consumer-config)))
(def producer (ckp/producer producer-config))
(ckp/send-messages producer [(test-message)])

(def message-stream (future (first (zk/messages consumer "test"))))

@message-stream
;; (.shutdown message-stream)
;; (first message-stream)

(defn consumer-config [] {"zookeeper.connect" "localhost:2182"
                          "group.id" (str (java.util.UUID/randomUUID))
                          "auto.offset.reset" "smallest"
                          "auto.commit.enable" "false"})
(defn fetch-first [topic]
  (with-resource [c (zk/consumer (consumer-config))]
    zk/shutdown
    (first (zk/messages c topic))))

(defn always-nil [topic batch-size]
  (with-resource [c (zk/consumer (consumer-config))]
    zk/shutdown
    (take 1 (zk/messages c topic))))

(fetch-first "test") ;=> #clj_kafka.core.KafkaMessage{:topic "test", :offset 0, :partition 0, :key nil, :value #object["[B" 0xcacb0b3 "[B@cacb0b3"]}
(always-nil "test" 1);=> ()

#clj_kafka.core.KafkaMessage{:topic "test", :offset 1, :partition 0, :key nil, :value #object["[B" 0x4ea01dc0 "[B@4ea01dc0"]}
#clj_kafka.core.KafkaMessage{:topic "test", :offset 0, :partition 0, :key nil, :value #object["[B" 0xbdbb381 "[B@bdbb381"]}
#clj_kafka.core.KafkaMessage{:topic "test", :offset 0, :partition 0, :key nil, :value #object["[B" 0x228f7871 "[B@228f7871"]}
#clj_kafka.core.KafkaMessage{:topic "test", :offset 0, :partition 0, :key nil, :value #object["[B" 0x55ae3567 "[B@55ae3567"]}
#clj_kafka.core.KafkaMessage{:topic "test", :offset 0, :partition 0, :key nil, :value #object["[B" 0x31213a5f "[B@31213a5f"]}

(def result
  (let [message-stream (zk/messages consumer "test")
        message (first message-stream)]
  (.shutdown message-stream)
  message))

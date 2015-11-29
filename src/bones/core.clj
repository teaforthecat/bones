(ns bones.core
  (:require [bones.system :as system]
            [bones.serializer :as ser]))

#_(def system (system/system {:env :test :port 3000}))

#_(alter-var-root #'system com.stuartsierra.component/start)
#_(alter-var-root #'system com.stuartsierra.component/stop)


#_(require '[clj-kafka.zk :as zk])
#_(require '[clj-kafka.core :as kafka])
#_(require '[clj-kafka.consumer.zk :as zkc])
#_(require '[clj-kafka.consumer.simple :as kc])
#_(require '[clj-kafka.new.producer :as kp])
#_(require '[clj-kafka.producer :as kop])
#_(require '[cognitect.transit :as transit])
#_(import '[java.io ByteArrayInputStream ByteArrayOutputStream])
#_(import '[org.apache.kafka.common.serialization Serializer])
#_(def broker-list
    (zk/broker-list
     (zk/brokers {"zookeeper.connect" "127.0.0.1:2181"})))



#_(def topic (last (zk/topics {"zookeeper.connect" "127.0.0.1:2181"})))

#_(def test-data "hello world!")

#_(def consumer-config {"zookeeper.connect" "127.0.0.1:2181"
                        "group.id" "clj-kafka.consumer"
                        "auto.offset.reset" "smallest"
                        "key.deserializer" "String" ;;"transit-decoder"
                        "auto.commit.enable" "false"})

#_(def producer-old-config
    {"metadata.broker.list" broker-list
     "serializer.class" "kafka.serializer.DefaultEncoder"
     "partitioner.class" "kafka.producer.DefaultPartitioner"
     "auto.commit.enable" "true"})


#_(def producer-config {"bootstrap.servers" broker-list})

(defn transit-encoder [{:keys [buffer data-format]}]
  (let [buff (or buffer (ByteArrayOutputStream. 4096))
        frmt (or data-format :msgpack)]
    (fn [data]
      (transit/write (transit/writer buff frmt) data)
      (.toByteArray buff))))



(defn transit-decoder [{:keys [buffer data-format]}]
  (let [buff (or buffer (ByteArrayOutputStream. 4096))
        frmt (or data-format :msgpack)]
    (fn [data]
      (transit/read (transit/reader buff frmt)))))

(def producer (kp/producer producer-config
                           (kp/byte-array-serializer)
                           (kp/byte-array-serializer)))

#_@(kp/send producer (kp/record topic ((transit-encoder {}) {:a 2 :b 3})))
;; broker-list  "192.168.2.7:9092"

#_(def p (kop/producer producer-old-config))

#_(kop/send-messages p [(kop/message topic (.getBytes "this is my message"))])


#_(def c (kc/consumer "localhost" 9092 "kc-consumer" :timeout 10))
#_(def consumerz (kc/messages c "kc-consumer" topic 0 0 1))
#_(take 2 consumerz)
#_(kc/latest-topic-offset c topic 0)

;; not working....
#_(def zc (zkc/consumer consumer-config))
#_(def z-consumerz (zkc/messages zc topic ))
#_(take 2 consumerz)

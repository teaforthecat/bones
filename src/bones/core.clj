(ns bones.core
  (:require [bones.system :as system]))


#_(def system (system/system {:env :test :port 3000}))

#_(.stop (:server system))
#_(.start (:server system))
#_(.restart (:server system))
#_(alter-var-root #'system com.stuartsierra.component/start)
#_(alter-var-root #'system com.stuartsierra.component/stop)


;; (comment

;; #_(require '[clj-kafka.zk :as zk])
;; #_(require '[clj-kafka.core :as kafka])
;; #_(require '[clj-kafka.consumer.zk :as zkc])
;; #_(require '[clj-kafka.consumer.simple :as kc])
;; #_ ;;(require '[kafka-clj.consumer.node :as fast])
;; #_(require '[clj-kafka.new.producer :as kp])
;; #_(require '[clj-kafka.producer :as kop])
;; #_(require '[cognitect.transit :as transit])
;; #_(import [java.io ByteArrayOutputStream ByteArrayInputStream])
;; #_(import [org.apache.kafka.common.serialization Serializer])

;; #_(def zookeeper-connect (get-in system [:zookeeper :config :zookeeper/address]))
;; #_(def broker-list
;;     "query zookeeper for the kafka servers to connect to in the bootstrap process (whatever that means)"
;;     (zk/broker-list
;;      (zk/brokers {"zookeeper.connect" zookeeper-connect})))



;; #_(def topic (last (zk/topics {"zookeeper.connect" "127.0.0.1:2181"})))

;; #_(def test-data "hello world!")

;; ;; #_(def consumer-config {"zookeeper.connect" "127.0.0.1:2181"
;; ;;                         "group.id" "clj-kafka.consumer"
;; ;;                         "auto.offset.reset" "smallest"
;; ;;                         "key.deserializer" "String" ;;"transit-decoder"
;; ;;                         "auto.commit.enable" "true"})

;; #_(def consumer-config {"zookeeper.connect" "localhost:2181"
;;                       "group.id" "clj-kafka.test.consumer"
;;                       "auto.offset.reset" "smallest"
;;                       "auto.commit.enable" "false"})

;; #_(def producer-old-config
;;     {"metadata.broker.list" broker-list
;;      "serializer.class" "kafka.serializer.DefaultEncoder"
;;      "partitioner.class" "kafka.producer.DefaultPartitioner"
;;      "auto.commit.enable" "true"})


;; #_(def producer-config {"bootstrap.servers" broker-list})

;; (comment ;; transit serialization
;;   (defn transit-encoder [data-format]
;;     (fn [data]
;;       (.toByteArray
;;        (let [buf (ByteArrayOutputStream. 4096)
;;              writer (transit/writer buf data-format)]
;;          (transit/write writer data)
;;          buf))))

;;   (defn transit-decoder [data-format]
;;     (fn [buffer]
;;       (transit/read (transit/reader (ByteArrayInputStream. buffer) data-format))))

;;   ((transit-decoder :json)
;;    ((transit-encoder :json) "hello"))
;;   )

;; (def producer (kp/producer producer-config
;;                            (kp/byte-array-serializer)
;;                            (kp/byte-array-serializer)))

;; (def topic "test-topic")
;; #_@(kp/send producer (kp/record "abc" ((transit-encoder {}) {:a 2 :b 3})))
;; #_@(kp/send producer (kp/record "abc" (.getBytes (pr-str :done))))

;; ;; broker-list  "192.168.2.7:9092"
;; (require '[onyx.kafka.utils :as kpu])

;; (def id (java.util.UUID/randomUUID))

;; (def zk-addr "127.0.0.1:2181")

;; (def peer-config
;;   {:zookeeper/address zk-addr
;;    :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
;;    :onyx.messaging/impl :aeron
;;    :onyx.messaging/peer-port 40199
;;    :onyx.messaging/bind-addr "localhost"
;;    :onyx/id id})

;; (def results
;;   (kpu/take-segments (:zookeeper/address peer-config) "abc" identity))


;; #_(def p (kop/producer producer-old-config))

;; #_(kop/send-message p (kop/message topic (.getBytes "this is my message")))

;; #_(def c (kc/consumer "localhost" 9092 "kc-consumer" :timeout 10))
;; #_(def consumerz (kc/messages c "kc-consumer" "test-topic" 0 0 1))
;; #_(take 1 consumerz)
;; #_(kc/latest-topic-offset c "test-topic" 0)



;; ;; working....
;; #_(def zc (zkc/consumer consumer-config))
;; #_(def zc (zkc/consumer {"zookeeper.connect" "localhost:2181"
;;                          "group.id" "clj-kafka.test.consumer"}))
;; #_(def z-consumerz (zkc/messages zc "poseidon-example" ))

;; #_(def m {"zookeeper.connect" "localhost:2181"
;;           "group.id" "clj-kafka.test.consumer"
;;           "auto.offset.reset" "smallest"
;;           "auto.commit.enable" "false"
;;           })

;; #_(def group-id "clj-kafka.test.consumer")

;; #_(defn consumer-first-config [] {"zookeeper.connect" zookeeper-connect
;;                                 "group.id" (str (java.util.UUID/randomUUID))
;;                                 "auto.offset.reset" "smallest"
;;                                 "auto.commit.enable" "false"})

;; #_(defn fetch-first []
;;     (let [first-message (kafka/with-resource [resource-c (zkc/consumer (consumer-first-config))]
;;                          zkc/shutdown
;;                           (first (zkc/messages resource-c "abc")))]
;;       ;((transit-decoder {}) (:value first-message))
;;       first-message))
;; ;; #clj_kafka.core.KafkaMessage{:topic "abc", :offset 0, :partition 0, :key nil, :value #object["[B" 0x43596959 "[B@43596959"]}


;; (last (zkc/messages (zkc/consumer (consumer-first-config) ) "abc")
;; (def first-message (fetch-first))

;; #_(zk/set-offset! m "clj-kafka.test.consumer" "poseidon-example" 0 0)

;; #_(def fast-consumer-conf {:bootstrap-brokers
;;                            [{:host "localhost" :port 9092}]
;;                            :redis-conf {:host "localhost"
;;                                         :port 6379
;;                                         :max-active 5
;;                                         :timeout 1000
;;                                         :group-name "test"}
;;                            :conf {}})

;; #_(def node (fast/create-node! consumer-conf ["ping"]))

;; (fast/read-msg! node)

;; ;;for a single message
;; (def m (fast/msg-seq! node))
;; ;;for a lazy sequence of messages
;; (add-topics! node ["test1" "test2"])
;; ;;add topics
;; (remove-topics! node ["test1"])
;; ;;remove topics

;; ;;when the consumer node is closed m will return nil after the last message,
;; ;;this allows for reading till closed blocking if waiting for messages and not shutdown

;; #_(doseq [msg (take-while (complement nil?) m)]
;;     (prn (:topic msg) " " (:partition msg) " " (:offset msg) " " (:bts msg)))

;; #_(fast/shutdown-node! node)
;;

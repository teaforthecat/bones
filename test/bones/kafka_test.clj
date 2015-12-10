(ns bones.kafka-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clj-kafka.zk :as zk]
            [clj-kafka.new.producer :as kp]
            [bones.serializer :refer [serialize deserialize]]))

(def run-count 1)
(def zookeeper-connect "127.0.0.1:2181")
(def broker-list
    (zk/broker-list
     (zk/brokers {"zookeeper.connect" zookeeper-connect})))
(def producer-config {"bootstrap.servers" broker-list})
(def producer (kp/producer producer-config
                           (kp/byte-array-serializer)
                           (kp/byte-array-serializer)))

(defn consumer-first-config [] {"zookeeper.connect" zookeeper-connect
                                "group.id" (str (java.util.UUID/randomUUID))
                                "auto.offset.reset" "smallest"
                                "auto.commit.enable" "false"})

(defn fetch-first [topic]
  (with-resource [c (zk/consumer (consumer-first-config))]
    zk/shutdown
    (first (zk/messages c topic))))

(defn handle-complex-command [segment]
  segment)

(defn handle-simple-command [segment]
  segment)

(def background-jobs
  [::handle-complex-command
   ::handle-simple-command])

(def background-onyx-config {})

(defn setup []
  (submit-jobs background-onyx-config
               (build-jobs background-onyx-config background-jobs)))

(defn send-to-background [segment]
  @(kp/send producer (kp/record ::handle-simple-command-input (serialize segment))))

;; TODO: namespaced symbol to string mapping and (string to namespaced symbol)
(defn read-from-background []
  (fetch-first ::handle-simple-command-input))


;; @WIP
(def input-equals-output
  (prop/for-all [v (gen/vector gen/int)]
                (let [segment v
                      submission (send-to-background (first background-jobs) v)]
                  (= segment
                     (read-from-background (first background-jobs))))))

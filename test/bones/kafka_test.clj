(ns bones.kafka-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [bones.kafka :as kafka]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.core.async :as a]
            [byte-streams :as bs]
            [clj-kafka.zk :as zk]
            [clj-kafka.new.producer :as kp]
            [bones.serializer :refer [serialize deserialize]]))


(deftest round-trip
  (testing "a simple string"
    ;; the consumer's offset is important here
    ;; TODO somehow get the right offset for this message
    (let [send-response @(kafka/produce "input" "simple-string" )
          consume-response (kafka/consume "input")]
      (is (= "simple-string" (:value consume-response)))
      consume-response))
  (testing "timeout"
    (let [consume-response (kafka/consume "not-a-topic")]
      (is (= "no messages" (:value consume-response))))))


(deftest personal-consumer
  (testing "receives a datum"
    (let [msg-ch (a/chan)
          shutdown-ch (a/chan)
          group-id "123"
          topic "abc"
          csmr (kafka/personal-consumer msg-ch shutdown-ch group-id topic) ]
      (try
        @(kafka/produce topic "456" "for-someone-else") ;; filtered out
        @(kafka/produce topic "123" "xyz")
        (is (= "xyz" (first (a/alts!! [(a/timeout 500) msg-ch]))))
        (finally
          (a/go (a/>! shutdown-ch :shutdown )))))))

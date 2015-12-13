(ns bones.kafka-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [bones.kafka :as kafka]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
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
      )))

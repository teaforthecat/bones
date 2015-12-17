(ns bones.jobs-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [schema.experimental.generators :as g]
            [schema.test]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [byte-streams :as bs]
            [expectations :refer [expect] :as expectations]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [bones.jobs :as jobs]))

(deftest job-sym-mapping
  (testing "symbol to string"
    (let [s (jobs/sym-to-topic :x.y/z)]
      (is (= s "x.y..z"))))
  (testing "string to symbol"
    (let [s (jobs/topic-to-sym "x.y..z")]
      (is (= s :x.y/z)))))

(deftest build-workflow-entry
  (testing "surrounds the symbol by other symbols: given a symbol x, create two vectors as: [[x-input x] [x x-output]]"
    (let [conf {}
          job-sym ::wat
          result (jobs/build-workflow-entry conf job-sym)]
      (is (= 2 (.length result)))
      (is (= 2 (.length (first result))))
      (is (= job-sym (last (first result))))
      (is (= job-sym (first (last result)))))))


(deftest build-catalog-entry
  (testing "names are correct"
    (let [conf {}
          job-sym ::wat
          result (jobs/build-catalog-for-job conf job-sym)]
      result)))

;; Test vars
(defn handle-complex-command [segment]
  segment)

(defn handle-simple-command [segment]
  segment)

(def background-jobs
  [:bones.jobs-test/handle-complex-command
   :bones.jobs-test/handle-simple-command])


(def onyx-peer-test-config
  {:onyx/id "abcd1234"
   :zookeeper/address "127.0.0.1:2181"
   :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
   :onyx.messaging/impl :aeron
   :onyx.messaging.aeron/allow-short-circuit? true
   :onyx.messaging/bind-addr "localhost"
   :onyx.messaging/peer-port 40200
   :onyx.messaging.aeron/embedded-driver? true})

;; (def background-onyx-config
;;   {:onyx/id "123"
;;    :onyx/batch-size 1
;;    :zookeeper/address "127.0.0.1:2181"
;;    :onyx.messaging/impl :aeron
;;    :onyx.peer/job-scheduler :onyx.job-scheduler/balanced
;;    :zookeeper.server/port 2181
;;    :onyx.messaging/bind-addr "localhost" } )

(def jobs
  (jobs/submit-jobs onyx-peer-test-config
                    (jobs/build-jobs onyx-peer-test-config background-jobs)))

(deftest submit-jobs
  (testing "complete submission works when a :job-id is returned"
    (let [result (jobs/submit-jobs background-onyx-config
                                   (jobs/build-jobs background-onyx-config background-jobs))]
      (is (:job-id (first result))))))

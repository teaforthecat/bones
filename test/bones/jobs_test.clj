(ns bones.jobs-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            ;; [schema.experimental.generators :as g]
            [schema.test]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [byte-streams :as bs]
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
    (let [job-sym :bones.jobs-test/wat
          result (jobs/workflow job-sym)]
      (is (= :bones.jobs-test..wat-input  (get-in result [0 0])))
      (is (= :bones.jobs-test/wat         (get-in result [0 1])))
      (is (= :bones.jobs-test/wat         (get-in result [1 0])))
      (is (= :redis-publisher             (get-in result [1 1]))))))


(deftest build-catalog-entry
  (testing "names are correct"
    (let [job-sym :bones.jobs-test/wat
          result (jobs/catalog job-sym)]
      (is (= :bones.jobs-test..wat-input (get-in result [0 :onyx/name])))
      (is (= "bones.jobs-test..wat-input" (get-in result [0 :kafka/topic])))
      (is (= :bones.jobs-test/wat (get-in result [1 :onyx/name])))
      (is (= :bones.jobs-test/wat (get-in result [1 :onyx/fn])))
      (is (= :redis-publisher (get-in result [2 :onyx/name])))
      (is (= :bones.jobs-test-output (get-in result [3 :onyx/name])))
      (is (= "bones.jobs-test-output" (get-in result [3 :kafka/topic]))))))

(deftest build-lifecycle-entry
  (testing "tasks are keywords"
    (let [job-sym :bones.jobs-test/wat
          result (jobs/lifecycle job-sym)]
      (is (= :bones.jobs-test..wat-input (get-in result [0 :lifecycle/task])))
      (is (= :bones.jobs-test-output (get-in result [1 :lifecycle/task]))))))


(deftest build-jobs
  (let [job-fns [:bones.jobs-test/wat]
        conf {:zookeeper/address "1.2.3.4:2181"
              :onyx.task-scheduler :onyx.task-scheduler/balanced
              :kafka/serializer-fn :something/else
              :kafka/deserializer-fn :something/other}]
    (testing "the configuration get applied correctly"
      (let [result (jobs/build-jobs conf job-fns)]
        (is (= "1.2.3.4:2181" (get-in result [0 :catalog 0 :kafka/zookeeper])))
        (is (= :something/else (get-in result [0 :catalog 3 :kafka/serializer-fn])))
        (is (= :something/other (get-in result [0 :catalog 0 :kafka/deserializer-fn])))
        (is (= :bones.jobs-test/wat (get-in result [0 :catalog 1 :onyx/fn])))
        (is (= :onyx.task-scheduler/balanced (get-in result [0 :task-scheduler])))))
    (testing "background-jobs can be built"
      (let [result (jobs/build-background-jobs conf job-fns)]
        (is (= :something/other             (get-in result [0 :catalog 0 :kafka/deserializer-fn])))
        (is (= :bones.jobs-test/wat         (get-in result [0 :catalog 1 :onyx/fn])))
        (is (= :bones.jobs-test/wat         (get-in result [0 :catalog 1 :onyx/name])))
        (is (= :bones.jobs-test/wat-results (get-in result [0 :catalog 2 :onyx/name])))
        ))))


(comment
  ;; example output
  [{:workflow [[:bones.jobs-test..wat-input :bones.jobs-test/wat]
               [:bones.jobs-test/wat :bones.jobs-test..wat-output]],
    :catalog [{:onyx/name :bones.jobs-test..wat-input,
               :onyx/plugin :onyx.plugin.kafka/read-messages,
               :onyx/batch-size 1,
               :onyx/type :input,
               :onyx/medium :kafka,
               :kafka/topic "bones.jobs-test..wat-input",
               :kafka/zookeeper "1.2.3.4:2181",
               :kafka/deserializer-fn :bones.serializer/deserializer}
              {:onyx/name :bones.jobs-test/wat,
               :onyx/fn :bones.jobs-test/wat,
               :onyx/batch-size 1,
               :onyx/type :function}
              {:onyx/name :bones.jobs-test..wat-output,
               :onyx/plugin :onyx.plugin.kafka/write-messages,
               :onyx/batch-size 1,
               :onyx/type :output,
               :onyx/medium :kafka,
               :kafka/topic "bones.jobs-test..wat-output",
               :kafka/zookeeper "1.2.3.4:2181",
               :kafka/serializer-fn :bones.serializer/serializer}],
    :lifecycles [{:lifecycle/task :bones.jobs-test..wat-input,
                  :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}
                 {:lifecycle/task :bones.jobs-test..wat-output,
                  :lifecycle/calls :onyx.plugin.kafka/write-messages-calls}],
    :task-scheduler :onyx.task-scheduler/balanced}]

  )

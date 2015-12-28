(ns bones.system-test
  (:require [com.stuartsierra.component :as component]
            [bones.http :refer [app]]
            [bones.jobs :as jobs]
            [system.components.aleph :refer [new-web-server]]
            [onyx.api]
            [onyx.log.zookeeper :refer [zookeeper]]
            [onyx.kafka.embedded-server :as ke]
            [clj-kafka.zk :as zk]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clj-kafka.producer :as kp]
            [schema.test]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [bones.system :as system]))

(use-fixtures :once schema.test/validate-schemas)

(def example-conf-file "resources/conf/xyz.edn")
(def test-conf-file "resources/conf/test.edn")

(defn provide-test-file [fn-test]
  (let [conf-file example-conf-file]
    (spit conf-file  (prn-str {:xyz "hello"}))
    (fn-test)
    (io/delete-file conf-file :silently)))

(use-fixtures :once provide-test-file)


(deftest configuration-system-test
  (testing "read-conf-data"
    (is (= {:xyz "hello"}
           (system/read-conf-data [example-conf-file]))))
  (testing "with all empty files"
    (is (.equals {:conf-files ["nothing" "also.nothing"]}
           (-> {:conf-files ["nothing" "also.nothing"]}
               (system/map->Conf)
               (.reload)))))
  (testing "conf reads edn files"
    (let [conf (.reload (system/map->Conf {:conf-files [example-conf-file]}))]
      (is (= "hello"
             (:xyz conf)))))
  (testing "system integration"
    (let [system (system/system {:conf-files [example-conf-file]})]
      (.start (:conf system)))))


(deftest jobs-system-test
  (testing "submit-jobs"
    (let [sys (atom (system/system {:conf-files [test-conf-file]}))]
      ;; starts just these components and does dependency injection
      (swap! sys com.stuartsierra.component/start-system [:jobs :conf])
      (is (not (empty? (get-in @sys [:jobs :submitted-jobs]))))
      (swap! sys com.stuartsierra.component/stop-system [:jobs :conf])

      (swap! sys com.stuartsierra.component/start-system [:jobs :conf])
      (is (empty? (get-in @sys [:jobs :submitted-jobs]))))))

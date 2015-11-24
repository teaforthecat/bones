(ns bones.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [ring.mock.request :as mock]
            [bones.core :refer :all]
            [byte-streams :as bs]))


(deftest ring-handler
  (testing "ring requests"
    (let [response @(handler (mock/request :get "/"))]
      (is (= 200 (:status response)))
      (is (= "Hello World!" (bs/convert (:body response) String))))))


(defspec first-element-is-min-after-sorting ;; the name of the test
  100 ;; the number of iterations for test.check to test
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
                (= (apply min v)
                   (first (sort v)))))

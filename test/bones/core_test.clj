(ns bones.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [ring.mock.request :as mock]
            [bones.http :refer [handler]]
            [schema.core :as s]
            [schema.test]
            [schema.experimental.complete :as c]
            [schema.experimental.generators :as g]
            [onyx-kafka.kafka/read-messages :refer [start-kafka-consumer
                                                    close-read-messages
                                                    close-write-resources]]
            [byte-streams :as bs]))


(use-fixtures :once schema.test/validate-schemas)

(deftest ring-handler
  (testing "ring requests"
    (let [response @(handler (mock/request :get "/"))]
      (is (= 200 (:status response)))
      (is (= "Hello World!\n" (bs/convert (:body response) String))))
    (let [response (handler (mock/request :get "/other" {:abc 123}))]
      (is (= 200 (:status response)))
      (is (= "Hello World!\n" (bs/convert (:body response) String))))
    ))


(deftest message-round-trip
  (testing "message-round-trip"
    (let [response @(handler (mock/request :get "/"))
          reader-ch (:kafka/reader-ch start-kafka-consumer)]
      (put-message)
      (is (= 200 (take! reader-ch)))
      (is (= "Hello World!\n" (bs/convert (:body response) String))))))



(comment
(s/defrecord StampedNames
  [date :- Long
   names :- [s/Str]])

(s/defn stamped-names :- StampedNames
  [names :- [s/Str]]
  (StampedNames. (str (System/currentTimeMillis)) names))

(comment
  (stamped-names ["hello" "goodbye"])


(s/explain StampedNames)
;; ==> (record user.StampedNames {:date java.lang.Long, :names [java.lang.String]})

(s/explain (s/fn-schema stamped-names))
;; ==> (=> (record user.StampedNames {:date java.lang.Long, :names [java.lang.String]})
;;         [java.lang.String])

;; And you can turn on validation to catch bugs in your functions and schemas
(s/with-fn-validation
  (stamped-names ["bob"]))
;==> RuntimeException: Output of stamped-names does not match schema:
;     {:date (not (instance? java.lang.Long "1378267311501"))}




(def FancyMap
  {
   :bar (s/enum :a :b :c)
   s/Str s/Str}

  )



(s/validate new-map {"a" "b" :foo :a})

(s/validate FancyMap {"a" "b" :foo :a})

(s/validate FancyMap {:foo :f "c" "d" "e" "f"})
  )

(defn first-element []
  (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
                (= (apply min v)
                   (first (sort v)))))

#_(tc/quick-check 10000 (first-element))

(doto [gen first-element]
  (defspec first-element-is-min-after-sorting times gen))

;; (defspec first-element-is-min-after-sorting ;; the name of the test
;;   100 ;; the number of iterations for test.check to test
;;   (prop/for-all [v (gen/not-empty (gen/vector gen/int))]
;;                 (= (apply min v)
;;                    (first (sort v)))))
)


(comment
  ; better integration with expectations

(defrecord SimpleCheck []
    CustomPred
    (expect-fn [e a] (:result a))
    (expected-message [e a str-e str-a] (format "%s of %s failures"
                                                                                              (:failing-size a)
                                                                                              (:num-tests a)))
    (actual-message [e a str-e str-a] (format "fail: %s" (:fail a)))
    (message [e a str-e str-a] (format "shrunk: %s" (get-in a [:shrunk :smallest]))))


(def prop-no-42
    (prop/for-all [v (gen/vector gen/int)]
                      (not (some #{42} v))))

(expect (->SimpleCheck) (tc/quick-check 100 prop-no-42))

)

(ns bones.http-test
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
            [bones.http :as http]))

(use-fixtures :once schema.test/validate-schemas)

(defn parse-body [response]
  (bs/convert (:body response) String))

(defn build-post [path body-schema]
  (mock/request :post path (g/generate body-schema)))

(defn build-get [path schema]
  (mock/request :get path (g/generate schema)))


(deftest test-query
  (testing "actual get"
    (is (= 200 (-> (p/session http/app)
                   (p/request "/api/query"
                              :request-method :get
                              :content-type "application/edn"
                              :query-params {:query {:x "y"}})
                   (:response)
                   (:status))))))

(deftest test-command
  (testing "actual post echoing the message"
    (let [command {:messag "HI!"}
          body (.getBytes (pr-str {:command command}))
          response (-> (p/session http/app)
                       (p/request "/api/command"
                                  :request-method :post
                                  :content-type "application/edn"
                                  :body body))]
      (is (= {:messag "HI!"}
             (-> response
                 (:response)
                 (parse-body)
                 (read-string)
                 (:message))))))
  (testing "x-sync true synchronous response"
    (let [command (g/generate http/Command)
          body (.getBytes (pr-str {:command command}))
          response (-> (p/session http/app)
                       (p/request "/api/command"
                                  :request-method :post
                                  :content-type "application/edn"
                                  :headers {"X-SYNC" true} ;;or lowercase
                                  :body body))]
      (is (= command (-> response
                           (:response)
                           (parse-body)
                           (read-string)
                           (:message))))
      (is (= "true" (-> response
                        :response
                        :headers
                        :x-sync))))))



(deftest test-send-input-command
  (testing "response generator"
    (let [response (http/send-input-command (g/generate http/Command))]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (= "input" (:topic (:body response))))))
  (testing "synchronous response round-trip"
    (let [command (g/generate http/Command)
          response (http/send-input-command command true)]
      (is (map? response))
      (is (= 200 (:status response)))
      (is (= "true" (:x-sync (:headers response))))
      (is (= command (:message (:body response)))))))

(ns bones.http-test
  (:require [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop :include-macros true]
            [schema.experimental.generators :as g]
            [schema.test]
            [manifold.stream]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [manifold.stream :as ms]
            [byte-streams :as bs]
            [clojure.core.async :as a]
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
    (let [command (g/generate http/Command)
          body (.getBytes (pr-str {:command command}))
          response (-> (p/session http/app)
                       (p/request "/api/command"
                                  :request-method :post
                                  :content-type "application/edn"
                                  :body body))]
      (is (= (bones.jobs/topic-name-output (:topic command))
             (-> response
                 (:response)
                 (parse-body)
                 (read-string)
                 (:topic)))))))

(deftest test-not-found
  (let [response (-> (p/session http/app)
                     (p/request "/nothing"))]
    (is (= 404 (:status (:response response))))
    (is (= "not found" (:body (:response response))))))

(deftest personal-subscriber
  (let [response ()]))

(deftest test-command-handler
  (testing "pub sub bus"
    (let [command (g/generate http/Command)
          output-topic (bones.jobs/topic-name-output (:topic command))
          mock-command-response {:key "user-123" :topic output-topic :offset 123}
          mock-stream (ms/->source (lazy-seq [{:key 1} mock-command-response {:key 2}]))]
      ;; (swap! http/consumer-registry assoc output-topic mock-stream)
      (let [response (http/command-handler command "user-id:123" true)]
        (is (= 200 (:status response)))))))

(deftest events-handler
  (testing "with a lazy-seq"
    (let [response (http/event-stream :topic-a (lazy-seq [1 2 3]) {"Mime-Type" "application/transit+json"})
          body (:body response)]
      (is (= "text/event-stream" (get-in response [:headers "Content-Type"])))
      (is (= "application/transit+json" (get-in response [:headers "Mime-Type"])))
      (is (= "event: :topic-a \ndata: 1 \n\n" @(manifold.stream/take! body) ))
      (is (= "event: :topic-a \ndata: 2 \n\n" @(manifold.stream/take! body) ))
      (is (= "event: :topic-a \ndata: 3 \n\n" @(manifold.stream/take! body) ))))
  (testing "with an core.async channel"
    (let [msg-chan (a/chan)
          response (http/event-stream :topic-a msg-chan)]
      (a/>!! msg-chan :hello )
      (is (= "event: :topic-a \ndata: :hello \n\n"
             @(ms/take! (:body response))))))
  (testing "events-handler"
    (let [command (g/generate http/Command)
          output-topic (bones.jobs/topic-name-output (:topic command))
          response (http/events-handler "123" "abc")]
      (is (= 200 (:status response)))))
  (testing "events-handler without user-id"
    (let [command (g/generate http/Command)
          output-topic (bones.jobs/topic-name-output (:topic command))
          response (http/events-handler nil "abc")]
      (is (= 401 (:status response)))))
  (testing "in the app"
    (let [command (g/generate http/Command)
          output-topic (bones.jobs/topic-name-output (:topic command))
          response (-> (p/session http/app)
                       (p/header "user-id" "123")
                       (p/request "/api/events"
                                  :request-method :get
                                  :query-params {:topic output-topic})
                       )]
      response)))

(ns bones.http
  (:require [bones.kafka :as kafka]
            [bones.jobs :as jobs]
            [ring.util.http-response :refer [ok service-unavailable header not-found]]
            [compojure.api.sweet :refer [defapi api context* GET* POST* ANY* swagger-docs swagger-ui]]
            [compojure.response :refer [Renderable]]
            [manifold.deferred :as d]
            [manifold.stream :as ms]
            [clojure.core.async :as a]
            [schema.core :as s]
            [byte-streams :as bs]))

;; passthrough to aleph
(extend-protocol Renderable
  manifold.deferred.Deferred
  (render [d _] d))

(s/defschema Command
  {:message s/Str
   :topic s/Str })

(s/defschema Query
  {:query {s/Keyword s/Str}})

(s/defschema Success
  {:mess s/Str})

(s/defschema QueryResult
  {:results s/Any})

(s/defschema KafkaResponse
  {(s/optional-key :topic) s/Str ; todo: remove optionals
   (s/optional-key :partition) s/Int
   (s/optional-key :offset) s/Int
   (s/optional-key :key) s/Str
   (s/optional-key :value) s/Any
   (s/optional-key :message) s/Any})


(defn command-handler [command & sync]
  (let [input-topic (jobs/topic-name-input (:topic command))
        output-topic (jobs/topic-name-output (:topic command))
        kafka-response @(kafka/produce input-topic (:message command))]
    (if (:topic kafka-response) ; not sure how to check for kafka errors here
      (if (first sync) ;;better optional arg?
        ;; is this io blocking?
        (-> (kafka/consume output-topic) ;; fixme return KafkaError
            (assoc :message (:message command) :topic output-topic)  ;;debugging
            (ok)
            (header :x-sync true)) ;;debugging
        (ok (assoc kafka-response :message (:message command))))
      (service-unavailable "command has not been received"))))

(defn query-handler [query]
  (ok {:results "HI!"}))

(defn event-stream [source serializer ]
  ;; todo add mime options
  {:status 200
   :headers {"Content-Type" "text/event-stream"
             "Cache-Control" "no-cache"
             "Connection" "keep-alive"
             "Mime-Type" "application/transit+msgpack"}
   :body (ms/transform
          ;; todo: event name and format options
          (map #(format "data: %s \n\n" (serializer %)))
          1 ;;buffer size
          (ms/->source source))})

#_@(kafka/produce "bcd" {:x "y"}) ;;testing
(defn events-handler [req]
  "a connection to the client stays open here"
  (let [user-id "abc"
        topic (:topic (:params req))]
    (let [[cnsmr messages] (kafka/open-consumer user-id topic)]
      ;; todo: better cleanup method
      (a/go (a/<! (a/timeout (* 10 1000))) (kafka/shutdown cnsmr))
      (event-stream messages (comp
                              #(bs/convert % String)
                              :value)))))


(def app
  (api
   {:formats [:json :edn]}
   (swagger-docs)
   (swagger-ui)
   (context* "/api" []
             (POST* "/command" []
                    :body-params [command :- Command]
                    :header-params [{x-sync false}]
                    ;; :return KafkaResponse or Error
                    (command-handler command x-sync))
             (GET* "/events" {:as req}
                   :query-params [topic :- s/Str]
                   (events-handler req))
             (GET* "/query" []
                   :query-params [query :- s/Any]
                   :return QueryResult
                   (query-handler query)))
   ;; todo: inject this route into development only somehow
   (ANY* "/echo" {:as req}
         (ok (assoc req :body (bs/convert (:body req) String))))
   (ANY* "/*" [] (not-found "not found"))))

#_(-> (app {:uri "/api/query"
            :request-method :get
            :query-params {:query {:x "y"}}})
      (:body)
      (byte-streams/convert String)) ;;=> "{\"results\":\"HI!\"}"

#_(-> (app {:uri "/api/events"
            :request-method :get
            :query-params {:query {:x "y"}}})
      (:body))

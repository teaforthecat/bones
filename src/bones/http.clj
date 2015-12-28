(ns bones.http
  (:require [bones.kafka :as kafka]
            [bones.jobs :as jobs]
            [ring.util.http-response :refer [ok service-unavailable header not-found]]
            [compojure.api.sweet :refer [defapi api context* GET* POST* ANY* swagger-docs swagger-ui]]
            [compojure.response :refer [Renderable]]
            [manifold.deferred :as d]
            [manifold.stream :as ms]
            [manifold.bus :as mb]
            [clojure.core.async :as a]
            [schema.core :as s]
            [byte-streams :as bs]))

;; passthrough to aleph
(extend-protocol Renderable
  manifold.deferred.Deferred
  (render [d _] d))

(def some-jobs
  {":bones.jobs-test/wat" (a/chan)
   ":bones.jobs-test/who" (a/chan)})

(s/defschema Command
  {:message {s/Any s/Any}
   :topic (s/enum ":bones.core/wat"
                  ":bones.core/who")})

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


(defn command-handler [command user-id sync]
  (let [input-topic (jobs/topic-name-input (:topic command))
        output-topic (jobs/topic-name-output (:topic command))
        message (merge (:message command) {:_kafka-key user-id})
        kafka-response @(kafka/produce input-topic user-id message)]
    (if (:topic kafka-response) ;; block for submitting to kafka
      (ok kafka-response) ;; return result of produce
      (service-unavailable "command has not been received"))))

(defn query-handler [query]
  (ok {:results "HI!"}))

(defn event-stream
  "Server Sent Events
  (http/event-stream :topic-a (lazy-seq [1 2 3]) pr-str {\"Mime-Type\" \"application/transit+json\"})"
  ([event-name source]
   (event-stream event-name source {}))
  ([event-name source headers]
   {:status 200 ;; to disconnect forever: HTTP 204 No Content
    :headers (merge {"Content-Type"  "text/event-stream"
                     "Cache-Control" "no-cache"
                     "Connection"    "keep-alive"}
                    headers)
    :body (ms/transform
           (map #(format "event: %s \ndata: %s \n\n" event-name %))
           1 ;;buffer size
           (ms/->source source))}))

#_@(kafka/produce "bcd" {:x "y"}) ;;testing: curl localhost:3000/api/events?topic=bcd
(defn events-handler [user-id topic]
  "a connection to the client stays open here"
  (let [msg-ch (a/chan)
        shutdown-ch (a/chan)
        group-id user-id]
    (if group-id
      (let [csmr (kafka/personal-consumer msg-ch shutdown-ch group-id topic)]
        (a/go (a/<! (a/timeout 60e3)) (a/>! shutdown-ch :shutdown))
        (event-stream topic msg-ch))
      {:status 401 :body "unauthorized" :headers {}})))

(def app
  (api
   {:formats [:json :edn]}
   (swagger-docs)
   (swagger-ui)
   (context* "/api" []
             (POST* "/command" {:as req}
                    :body-params [command :- Command]
                    :header-params [{x-sync false}  {user-id 0}]
                    ;; :return KafkaResponse or Error
                    ;; TODO: get real user-id
                    (command-handler command (str user-id) x-sync))

             (GET* "/events" {:as req}
                   :query-params [topic :- s/Str]
                   :header-params [{user-id 0}]
                   (events-handler user-id topic))
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
;; time curl localhost:3000/api/command -XPOST -H "Content-Type: application/edn"  -v -d '{:command {:topic ":bones.jobs-test/who" :message "helloxfz"}}'  -H "user-id: 123"
;; time curl localhost:3000/api/command -XPOST -H "Content-Type: application/edn"  -v -d '{:command {:topic ":bones.jobs-test/who" :message "helloxfz"}}'  -H "user-id: 123" -H "X-SYNC: true"

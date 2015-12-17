(ns bones.http
  (:require [bones.kafka :as kafka]
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
  {:messag s/Str})

(s/defschema Query
  {:query {s/Keyword s/Str}})

(s/defschema Success
  {:mess s/Str})

(s/defschema QueryResult
  {:results s/Any})

(s/defschema KafkaResponse
  {:topic s/Str
   :partition s/Int
   :offset s/Int
   (s/optional-key :key) s/Str
   (s/optional-key :value) s/Any
   (s/optional-key :message) s/Any})

(defn command-handler [command]
  (ok {:mess (str "success-" (:messag command))}))


(defn send-input-command [command & sync]
  (let [kafka-response @(kafka/produce "bones.jobs-test..handle-simple-command-input..123" "heelo")]
    (if (:topic kafka-response) ; not sure how to check for kafka errors here
      (if sync
        ;; is this io blocking?
        (-> {:topic "bones.jobs-test..handle-simple-command-output" :partition 0 :offset 0}
            (merge (kafka/consume "bones.jobs-test..handle-simple-command-input")) ;; fixme return KafkaError
            (assoc :message command)
            (ok)
            (header :x-sync true))
        (ok (assoc kafka-response :message command)))
      (service-unavailable "command has not been received"))))

(defn query-handler [query]
  (ok {:results "HI!"}))

(defn sse-handler [req]
  "a connection to the client stays open here"
  (let [listener-ch (a/chan)
        renderer-ch (a/chan)
        shutdown-ch (a/chan)]

    (kafka/personal-consumer listener-ch
                             shutdown-ch
                             "xyzabc1234"
                             "bones.jobs-test..handle-simple-command-input..123"))

    (a/go-loop [i 0]
      (if (< i 10)
        (do
           (let [m (a/<! listener-ch)]
             (a/>! renderer-ch (->> (update m :value #'bones.serializer/deserialize )
                                    (pr-str)
                                    (format "data: %s \n\n")))
             (recur (inc i))))
        (do
          (a/put! shutdown-ch :shutdown)
          (a/close! renderer-ch))))

    {:status 200
     :headers {"Content-Type" "text/event-stream"
               "Cache-Control" "no-cache"
               "Connection" "keep-alive"}
     :body (ms/->source renderer-ch)}))


(def app
  (api
   {:formats [:json :edn]}
   (swagger-docs)
   (swagger-ui)
   (context* "/api" []
             (POST* "/command" []
                    :body-params [command :- Command]
                    :header-params [{x-sync false}]
                    ;; :return KafkaResponse
                    (send-input-command command x-sync))
             (GET* "/events" {:as req}
                   (sse-handler req))
             (GET* "/query" []
                   :query-params [query :- s/Any]
                   :return QueryResult
                   (query-handler query)))
   ;; todo inject this route into development only somehow
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
      (:body)) ;;=> "{\"results\":\"HI!\"}"

(ns bones.http
  (:require [bones.kafka :as kafka]
            [ring.util.http-response :refer [ok service-unavailable header not-found]]
            [compojure.api.sweet :refer [defapi api context* GET* POST* ANY* swagger-docs swagger-ui]]
            [schema.core :as s]
            [byte-streams :as bs]))

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
  (let [kafka-response @(kafka/produce "input" command)]
    (if (:topic kafka-response) ; not sure how to check for kafka errors here
      (if sync
        ;; is this io blocking?
        (-> (kafka/consume "output")
            (merge {:topic "output" :partition 0 :offset 0}) ;; fixme return KafkaError
            (assoc :message command)
            (ok)
            (header :x-sync true))
        (ok (assoc kafka-response :message command)))
      (service-unavailable "command has not been received"))))

(defn query-handler [query]
  (ok {:results "HI!"}))

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

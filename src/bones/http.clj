(ns bones.http
  (:require [bones.kafka :as kafka]
            [ring.util.http-response :refer [ok service-unavailable]]
            [compojure.api.sweet :refer [defapi api context* GET* POST* swagger-docs swagger-ui]]
            [schema.core :as s]))

(s/defschema Command
  {:messag s/Str})

(s/defschema Success
  {:mess s/Str})

(s/defschema KafkaResponse
  {:topic s/Str
   :partition s/Int
   :offset s/Int})

(defn command-handler [command]
  (ok {:mess (str "success-" (:messag command))}))


(defn send-input-command [command]
  (let [kafka-response @(kafka/send "input" command)]
    (if (:topic kafka-response)
      (ok kafka-response)
      (service-unavailable "response has not been received"))))

(defapi app
  {:formats [:json :edn]}
  (swagger-docs)
  (swagger-ui)
  (context* "/api" []
            (POST* "/command/:id" []
                   :path-params [id :- s/Int]
                   :body [command Command {:description "this input will be serialized in kafka and stored forever"}]
                   :return KafkaResponse ;Success
                   (send-input-command command)
                   )))

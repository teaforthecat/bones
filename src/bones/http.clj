(ns bones.http
  (:require [bones.kafka :as kafka]
            [bones.jobs :as jobs]
            [bones.log :as logger]
            [taoensso.timbre :as log]
            [ring.util.http-response :refer [ok service-unavailable header not-found bad-request unauthorized internal-server-error]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.session :refer [wrap-session]]
            [compojure.api.sweet :refer [defroutes* defapi api context* GET* POST* OPTIONS* ANY* swagger-docs swagger-ui]]
            [compojure.response :refer [Renderable]]
            [compojure.api.exception :as ex] ;; for ::ex/default
            [manifold.deferred :as d]
            [manifold.stream :as ms]
            [manifold.bus :as mb]
            [clojure.core.async :as a]
            [schema.core :as s]
            [prone.stacks]
            [prone.middleware :as prone]
            [prone.debug :refer [debug]]
            [clj-time.core :as time]
            [buddy.sign.jwe :as jwe]
            [buddy.core.nonce :as nonce]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jwe-backend]]
            [buddy.auth.backends.session :refer [session-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers :as hashers]
            [datascript.core :as ds]
            [byte-streams :as bs]))

(def example-uuid (java.util.UUID/randomUUID))

(def some-jobs
  {:bones.core/wat {:weight-kg s/Num
                    :name s/Str}
   :bones.core/who {:name s/Str
                    :role s/Str}})

(def secret (nonce/random-bytes 32))
(def algorithm {:alg :a256kw :enc :a128gcm})
(def auth-backend (jwe-backend {:secret secret :options algorithm}))
(def cookie-session-backend (session-backend))

(defn cookie-session-middleware [handler]
  " a 16-byte secret key so sessions last across restarts"
  (wrap-session handler {:store (cookie-store {:key "a 16-byte secret"})
                         ;; TODO: add :secure true
                         :cookie-name "bones-session"
                         :cookie-attrs {:http-only false}}))

;; passthrough to aleph
(extend-protocol Renderable
  manifold.deferred.Deferred
  (render [d _] d))

(defn event-stream
  "Server Sent Events"
  ([event-name source]
   (event-stream event-name source {}))
  ([event-name source headers]
   {:status 200
    :headers (merge {"Content-Type"  "text/event-stream"
                     "Cache-Control" "no-cache"
                     "Connection"    "keep-alive"}
                    headers)
    :body (ms/transform
           (map #(format "data: %s \n\n" %))
           ;; no worky in browser:
           ;; (map #(format "event: %s \ndata: %s \n\n" event-name %))
           1 ;;buffer size
           (ms/->source source))}))

(s/defschema QueryResult
  {:results s/Any})

(def users
  [  {:username "admin"
      :password "secret"
      :roles [:admin]}
   {:username "jerry"
    :password "jerry"}])

(def schema {})
(def conn (ds/create-conn schema))

(defn create-user [{:keys [username password roles]}]
  (let [enc-pass (hashers/encrypt password {:alg :bcrypt+blake2b-512})
        new-user {:db/id -1
                  :username username
                  :password enc-pass
                  :roles (or roles [:new-user])}]
    (ds/transact! conn [ new-user ])))
#_(map create-user users)

(defn find-user [username]
  (let [db @conn]
    (->> username
         (ds/q '[:find ?id ?roles ?password
                 :in $ ?username
                 :where [?id :username ?username]
                 [?id :roles ?roles]
                 [?id :password ?password]
                 ]
               db
               username)
         first
         (zipmap [:id :roles :password]))))

(defn check-password [username password]
  (let [user (find-user username)
        pass (hashers/check password (:password user))]
    (if pass
      (dissoc user :password))))

(defn login-handler [username password]
  (let [user-data (check-password username password)]
    (if user-data
      (let [claims {:user user-data
                    :exp (time/plus (time/now) (time/hours 1))}
            token (jwe/encrypt claims secret algorithm)]
        ;;setup both token and cookie
        ;;for /api/events EventSource
        (-> (ok {:token token})
            (update :session merge {:identity {:user user-data}})))
      (bad-request {:message "username or password is invalid"}))))

(defn command-handler [job-topic message req]
  (let [user-id (get-in req [:identity :user :id])
        job-fn (jobs/topic-to-sym job-topic);; this is a funny dance
        input-topic (jobs/topic-name-input job-fn)
        output-topic (jobs/topic-name-output job-fn)
        ;; uuid is optional :_kafka-key is not
        meta-data (merge (select-keys (:params req) [:uuid]) {:_kafka-key user-id})
        ;; store auth key for output topic
        data (merge {:message message} meta-data)
        kafka-response @(kafka/produce input-topic user-id data)]
    (if (:topic kafka-response) ;; block for submitting to kafka
      (ok kafka-response) ;; return result of produce
      (service-unavailable "command has not been received"))))

(defn query-handler [query]
  (ok {:results "HI!"}))

(defn events-handler [topic req]
  "a connection to the client stays open here"
  (let [user-id (get-in req [:identity :user :id])
        wat-user-id (or user-id (get-in req [:identity :id]))
        msg-ch (a/chan)
        shutdown-ch (a/chan)]
    (if user-id
      ;; FIXME: only support one topic for now?
      (do
        (log/info "starting kafka consumer")
        (let [csmr (kafka/personal-consumer msg-ch shutdown-ch user-id topic)]
          ;; if the csmr receives a message between the time the user closes a connection
          ;; and this timeout fires, the user will never get the message
          ;; todo: make this a configurable value, 1 minute is just a guess at a reasonable lifespan
          (a/go (a/<! (a/timeout 60e3))
                (a/>! msg-ch :reconnect)
                (a/>! shutdown-ch :shutdown))
          ;; TODO: add MIME-Type
          (event-stream topic msg-ch)))
      {:status 401 :body "unauthorized" :headers {:content-type "application/edn"}})))


(defmacro make-commands [job-specs]
  (map
   (fn [job-spec]
     (let [[job-fn spec] job-spec
           job-topic (bones.jobs/sym-to-topic job-fn)]
       `(POST* ~(str "/command/" job-topic) {:as ~'req}
               :body-params [~'message :- ~spec {~'uuid :- s/Uuid example-uuid}] ;; support "no nils"  in swagger-ui
               :header-params [{~'AUTHORIZATION "Token: xyz"}]
               (if (~'buddy.auth/authenticated? ~'req)
                 (command-handler ~job-topic ~'message ~'req)
                 (~'ring.util.http-response/unauthorized "Valid Auth Token required")
                 ))))
   (eval job-specs)))

;; this could probably go away. prone isn't vary helpful with ajax requests, but the few stacktraces are better than nothing
(defn api-ex-handler [error error-type request]
  (internal-server-error {:message (.getMessage error)
                          :stacktrace (take 3 (map prone.stacks/normalize-frame (.getStackTrace error) ))
                          :class (.getName (.getClass error))}))

(defn cqrs [path jobs]
  (let [outputs (map (comp bones.jobs/topic-name-output first) jobs)
        outputs-enum `((s/enum ~@outputs))
        commands `(make-commands ~jobs)]
    `(api
      {:formats [:json :edn]
       :api-key.name "AUTHORIZATION"
       :api-key.in "header"
       :exceptions {:handlers {::ex/default api-ex-handler}}}
      (swagger-ui)
      (swagger-docs)
      (context* ~path []
                :tags ["cqrs"]
                ~@(macroexpand commands)
                (GET* "/events" {:as ~'req}
                      :query-params [~'topic :- ~@outputs-enum]
                      :header-params [{~'AUTHORIZATION "Token: xyz"}]
                      (if (~'buddy.auth/authenticated? ~'req)
                        (events-handler ~'topic ~'req)
                        (~'ring.util.http-response/unauthorized "Authentication Token required")))
                (GET* "/query" {:as ~'req}
                      :query-params [~'query :- s/Any]
                      :return QueryResult
                      :header-params [{~'AUTHORIZATION "Token: xyz"}]
                      (if (~'buddy.auth/authenticated? ~'req)
                        (query-handler ~'query)
                        (~'ring.util.http-response/unauthorized "Valid Auth Token required"))))
      (POST* "/login" {:as ~'req}
         :tags ["login"]
         :body-params [~'username :- s/Str ~'password :- s/Str]
         (login-handler ~'username ~'password))
      (OPTIONS* "/*" [] (ok "for cors"))
      (ANY* "/*" [] (not-found "not found")))))


(s/defschema HandlerConf
  {:bones.http/path s/Str
   :bones/jobs {s/Keyword (s/protocol s/Schema)}
   s/Any s/Any})


(def cors-headers
  {"Access-Control-Allow-Origin" "http://localhost:3449"
   "Access-Control-Allow-Headers" "Content-Type,AUTHORIZATION"
   "Access-Control-Allow-Methods" "GET,POST,OPTIONS"
   "Access-Control-Allow-Credentials" "true"})

(defn all-cors
  "Allow requests from all origins"
  [handler]
  (fn [request]
    (let [response (handler request)]
      (update-in response [:headers]
        merge cors-headers ))))

(defn build-handler [conf]
  (s/validate HandlerConf conf)
  (let [{:keys [:bones.http/path :bones/jobs]} conf]
    (all-cors
     (logger/wrap-with-body-logger
      (cookie-session-middleware
       (wrap-authentication
        (eval (cqrs path jobs))
        auth-backend
        cookie-session-backend)
       )))))

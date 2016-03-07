(ns bones.http
  (:require [bones.kafka :as kafka]
            [bones.jobs :as jobs]
            [bones.log :as logger]
            [bones.serializer :refer [serialize deserialize]]
            [aleph.http :as http]
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


;; ##########
;; # Web Socket
;; ##########
(def non-websocket-request
  {:status 400
   :headers {"content-type" "application/text"}
   :body "Expected a websocket request."})

(defn ws-handler [topic req]
  "a websocket connection to the client stays open here"
  (let [user-id (get-in req [:identity :user :id])
        wat-user-id (or user-id (get-in req [:identity :id]))]
    (if user-id
      (do
        (let [conn (d/catch
                       @(http/websocket-connection req)
                       (fn [_] nil))]
          (if conn
            (let [consumer-config {"zookeeper.connect" "127.0.0.1:2181"
                                   "group.id" (str user-id)
                                   "auto.offset.reset" "smallest"}
                  [cnmr messages] (kafka/output-stream consumer-config topic)
                  message-source (ms/->source messages)
                  real-message-source (ms/transform #(deserialize (:value %)) message-source)
                  ]
              (ms/on-closed conn #(.shutdown cnmr))
              (ms/on-closed conn #(log/info "conn closed"))
              (ms/consume #(ms/put! conn (deserialize (:value %))) message-source)
              (ms/consume #(ms/put! conn (:value %)) (ms/->source (lazy-seq [{:value "hello"}])))

              (ms/connect
               message-source
               conn)
              )
              (do
                (log/info "invalid websocket request")
                non-websocket-request))))
      (do
        (log/info "unauthorized websocket request")
        (unauthorized)))))

;; ##########
;; # Events
;; ##########

(def consumer-registry (atom {}))

(defn register-consumer [user-id consumer]
  (swap! consumer-registry update user-id (comp set conj) consumer))

(defn unregister-consumer [user-id consumer]
  ;; since user gets only one topic/consumer
  ;; todo maybe add topic filter
  (swap! consumer-registry assoc user-id #{}))

;; this only makes sense if there is only one webserver, which is crazy
;; perhaps a load balancer will force a user to use one webserver (?)
;; at least reloading the page during development will work
(defn find-consumer [user-id & topics]
  (let [cnmrs (get @consumer-registry user-id)]
    (if (not-empty topics)
      (filter #(some #{(:topic %)} topics) ;;weird include? predicate
              cnmrs)
      cnmrs)))

;; all this consumer-registry stuff is of questionable value
(defn close-consumer [{:keys [msg-ch shutdown-ch] :as consumer}]
  (unregister-consumer consumer)
  (a/>!! msg-ch :reconnect)
  (a/>!! shutdown-ch :shutdown))

(defn event-stream-close-handler [req]
  (let [user-id (get-in req [:identity :user :id])
        ;; todo add topic filter param
        consumers (find-consumer user-id)]
    (map close-consumer consumers)
    (ok (str "closed consumers: " (map :topic consumers)))))

(defn events-handler [topic req]
  "a connection to the client stays open here"
  (let [user-id (get-in req [:identity :user :id])
        wat-user-id (or user-id (get-in req [:identity :id]))
        {:keys [msg-ch shutdown-ch]} (first (find-consumer user-id topic))]
    (if user-id
      (do
        (if msg-ch
          (do
            (log/info "found kafka consumer on topic: " topic)
            (event-stream topic msg-ch))
          (let [msg-ch (a/chan)
                shutdown-ch (a/chan)
                cnsmr (kafka/personal-consumer msg-ch shutdown-ch user-id topic)]
            (log/info "starting kafka consumer on topic: " topic)
            (a/go (a/<! (a/timeout 10e3))
                  (close-consumer cnsmr))
            (register-consumer user-id {:topic topic :shutdown-ch shutdown-ch :msg-ch msg-ch})
            (event-stream topic msg-ch))))
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
  (let [fn-outputs (map (comp bones.jobs/topic-name-output first) jobs)
        ;;; todo support multiple namespaces - oh my
        general-outputs (map (comp bones.jobs/ns-name-output first) jobs)
        outputs (concat fn-outputs general-outputs)
        ;;;aeeihotuhnaohunotuhsontuhaso
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
                (POST* "/events/close" {:as ~'req}
                       (if (~'buddy.auth/authenticated? ~'req)
                         (event-stream-close-handler ~'req)
                         (~'ring.util.http-response/unauthorized "Authentication Token required")))
                (GET* "/events" {:as ~'req}
                      :query-params [~'topic :- ~@outputs-enum]
                      :header-params [{~'AUTHORIZATION "Token: xyz"}]
                      (if (~'buddy.auth/authenticated? ~'req)
                        (events-handler ~'topic ~'req)
                        (~'ring.util.http-response/unauthorized "Authentication Token required")))
                (GET* "/ws" {:as ~'req}
                      :query-params [~'topic :- ~@outputs-enum]
                      :header-params [{~'AUTHORIZATION "Token: xyz"}]
                      (if (~'buddy.auth/authenticated? ~'req)
                        (ws-handler ~'topic ~'req)
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

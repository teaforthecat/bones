(ns bones.system
  (:require [com.stuartsierra.component :as component]
            [bones.http :refer [app]]
            [system.components.aleph :refer [new-web-server]]
            [onyx.api]
            [onyx.log.zookeeper :refer [zookeeper]]
            [onyx.kafka.embedded-server :as ke]
            [clj-kafka.zk :as zk]
            [clj-kafka.producer :as kp]))

(def onyx-zk-test-config
  {:onyx/id "abcd1234"
   :zookeeper/server? true
   :zookeeper.server/port 2181
   :zookeeper/address "127.0.0.1:2181"})


(def onyx-peer-test-config
  {:onyx/id "abcd1234"
   :zookeeper/address "127.0.0.1:2181"
   :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
   :onyx.messaging/impl :aeron
   :onyx.messaging.aeron/allow-short-circuit? true
   :onyx.messaging/bind-addr "localhost"
   :onyx.messaging/peer-port 40200
   :onyx.messaging.aeron/embedded-driver? true})


(def onyx-peer-dev-config
  (assoc onyx-peer-test-config
         :onyx.messaging.aeron/allow-short-circuit? false))


;; this is for onyx.api/submit-job
(def background-onyx-config
  {:onyx/id  "abcd1234"
   :onyx/batch-size 1
   :zookeeper/address "127.0.0.1:2181"
   :onyx.messaging/impl :aeron
   :onyx.peer/job-scheduler :onyx.job-scheduler/greedy
   :zookeeper.server/port 2181
   :onyx.messaging/peer-port 40201
   :onyx.messaging/bind-addr "localhost" } )


(defmulti system :env)
(defrecord Conf []
  component/Lifecycle
  (start [cmp]
    (merge cmp {
                :serializer {:format :msgpack} ;; or json or ..
                }))
  (stop [cmp] {};;empty
        ))

(defrecord OnyxPeerGroup [config]
  component/Lifecycle
  (start [cmp]
    (when-not (:peer-group cmp)
      (assoc cmp
             :peer-group
             (onyx.api/start-peer-group config))))
  (stop [cmp]
    (when-let [pg (:peer-group cmp)]
      (try
        (onyx.api/shutdown-peer-group pg)
        (catch InterruptedException e)))))

(defrecord OnyxPeers [n-peers onyx-peer-group]
  component/Lifecycle
  ;; requires OnyxPeerGroup
  ;; using dependecy injection of n-peers onyx-peer-group
  (start [cmp]
    (when-not (:peers cmp)
      (assoc cmp
             :peers
             (onyx.api/start-peers n-peers (:peer-group onyx-peer-group)))))
  (stop [cmp]
    (when-let [pg (:peers cmp)]
      (doseq [v-peer (:peers cmp)]
        (try
          (onyx.api/shutdown-peer v-peer)
          (catch InterruptedException e)
          (finally
            (dissoc cmp :peers)))))))


;; aka: :test
(defmethod system :default [{:keys [port env] :as config}]
  (component/system-map
   :server (new-web-server port #'app)
   :zookeeper (zookeeper onyx-zk-test-config) ;; this will need to be reused in onyx config
   :kafka  (component/using
            (ke/map->EmbeddedKafka
             {:hostname "localhost"
              :port 9092
              :broker-id 0
              ;; :log-dir "/tmp/embedded-kafka" default
              :zookeeper-addr (:zookeeper/address onyx-zk-test-config)})
            ;; todo write another component instead of this one?
            ;; add dependency on zookeeper service
            [:zookeeper])

   :onyx-peer-group (component/using
                     (map->OnyxPeerGroup {:config onyx-peer-test-config})
                     ;; todo use the dependency injection, conf is available as a variable
                     [:conf :kafka])

   :onyx-peers (component/using
                (map->OnyxPeers {:n-peers 4})
                [:onyx-peer-group])
   ;; todo: look into component/system-using
   ;; :producer (component/using

   ;;            [:kafka])
   :conf (map->Conf {})))

(defmethod system :dev [{:keys [port env]  :as config}]
  (component/system-map
   :server (new-web-server port #'app)))

(defmethod system :prod [{:keys [port env] :as config}]
  (component/system-map
   :server (new-web-server port #'app)))

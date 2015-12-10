(ns bones.system
  (:require [com.stuartsierra.component :as component]
            [bones.http :refer [app]]
            [system.components.aleph :refer [new-web-server]]
            [onyx.log.zookeeper :refer [zookeeper]]
            [onyx.kafka.embedded-server :as ke]
            [clj-kafka.zk :as zk]
            [clj-kafka.producer :as kp]))

(def onyx-zk-test-config
  {:onyx/id "abcd1234"
   :zookeeper/server? true
   :zookeeper.server/port 2181
   :zookeeper/address "127.0.0.1:2181"})


(defmulti system :env)
(defrecord Conf []
  component/Lifecycle
  (start [cmp]
    (merge cmp {
                :serializer {:format :msgpack} ;; or json or ..
                }))
  (stop [cmp] {};;empty
        ))

;; aka: :test
(defmethod system :default [{:keys [port env] :as config}]
  (component/system-map
   ;; :server (new-web-server port #'handler)
   :server (new-web-server port #'app)
   :zookeeper (zookeeper onyx-zk-test-config) ;; this will need to be reused in onyx config
   :kafka  (component/using
            (ke/map->EmbeddedKafka
             {:hostname "localhost"
              :port 9092
              :broker-id 0
              ;; :log-dir "/tmp/embedded-kafka" default
              :zookeeper-addr (:zookeeper/address onyx-zk-test-config)})
            ;; add dependency on zookeeper service
            [:zookeeper])
   ;; :producer (component/using

   ;;            [:kafka])
   :conf (map->Conf {})))

(defmethod system :dev [{:keys [port env]  :as config}]
  (component/system-map
   :server (new-web-server port #'app)))

(defmethod system :prod [{:keys [port env] :as config}]
  (component/system-map
   :server (new-web-server port #'app)))

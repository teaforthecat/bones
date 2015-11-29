(ns bones.system
  (:require [com.stuartsierra.component :as component]
            [bones.http :refer [handler]]
            [system.components.aleph :refer [new-web-server]]
            [onyx.log.zookeeper :refer [zookeeper]]
            [onyx.kafka.embedded-server :as ke]))

(def onyx-zk-test-config
  {:onyx/id "abcd1234"
   :zookeeper/server? true
   :zookeeper.server/port 3181
   :zookeeper/address "127.0.0.1:2181"})


(defmulti system :env)

;; aka: :test
(defmethod system :default [{:keys [port env] :as config}]
  (component/system-map
   :server (new-web-server port #'handler)
   :zookeeper (zookeeper onyx-zk-test-config) ;; this will need to be reused in onyx config
   :kafka  (ke/map->EmbeddedKafka
            {:hostname "localhost"
             :port 9092
             :broker-id 0
             ;; :log-dir "/tmp/embedded-kafka" default
             :zookeeper-addr (:zookeeper/address onyx-zk-test-config)})))

(defmethod system :dev [{:keys [port env]  :as config}]
  (component/system-map
   :server (new-web-server port #'handler)))

(defmethod system :prod [{:keys [port env] :as config}]
  (component/system-map
   :server (new-web-server port #'handler)))

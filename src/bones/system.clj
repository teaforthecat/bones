(ns bones.system
  (:require [com.stuartsierra.component :as component]
            [bones.http :refer [handler]]
            [system.components.aleph :refer [new-web-server]]
            [onyx.kafka.embedded-server :as ke]))


(defmulti system :env)

;; aka: :test
(defmethod system :default [{:keys [port]}]
  (component/system-map
   :server (new-web-server port #'handler)
   :kafka  (ke/map->EmbeddedKafka
            {:hostname "127.0.0.1"
             :port 9092
             :broker-id 0
             :num-partitions 1
             ;; :log-dir "/tmp/embedded-kafka" default
             :zookeeper-addr "127.0.0.1:2181"})))

(defmethod system :dev [{:keys [port]}]
  (component/system-map
   :server (new-web-server port #'handler)))

(defmethod system :prod [{:keys [port]}]
  (component/system-map
   :server (new-web-server port #'handler)))

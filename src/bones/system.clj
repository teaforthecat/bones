(ns bones.system
  (:require [com.stuartsierra.component :as component]
            [bones.core :refer [handler]]
            [system.components.aleph :refer [new-web-server]]))


(defmulti system :env)

(defmethod system :test [args]
  (component/system-map
   :server (new-web-server 3000 #'handler)))

(defmethod system :dev [args]
  (component/system-map
   :server (new-web-server 3000 #'handler)))

(defmethod system :prod [args]
  (component/system-map
   :server (new-web-server 3000 #'handler)))

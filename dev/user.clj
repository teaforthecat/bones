(ns user
  (:require [figwheel-sidecar.repl-api :as ra]
            [bones.system :as system]
            [userspace.core]))

(defn bootup []
  (system/start-system userspace.core/sys :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)
  (userspace.core/seed))

(defn bootdown []
  (system/stop-system userspace.core/sys))


(defn start [] (ra/start-figwheel!))

(defn stop [] (ra/stop-figwheel!))

(defn cljs [] (ra/cljs-repl "dev"))

(ns bones.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [bones.handlers :as handlers]
            [bones.html :as html]
            [cljs.core.async :refer [<! >! take! put! timeout]]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

;; todo: for development only
(enable-console-print!)

(defn- ^:export main []
  "To be executed on page load. The main point of entry for the app."
  (println "initializing the database")
  (bones.db/setup)
  (handlers/setup)
  (html/main))

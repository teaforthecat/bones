(ns bones.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [bones.handlers :as handlers]
            [bones.html :as html]
            [bones.event-source :as es]
            [com.stuartsierra.component :as component]
            [cljs.core.async :as a]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

;; todo: for development only
(enable-console-print!)

;; this should be configurable duh
(def config
  {:event-source-url "http://localhost:3000/api/events?topic=userspace.jobs..wat-output"})

(defn system [config]
  (atom (component/system-map
         :event-source (es/event-source (:event-source-url config)
                                        (a/chan)))))
(def sys (system config))

(defn listener [msg-ch]
  (go-loop [n 0]
    (let [msg (<! msg-ch)]
      (handlers/dispatch [:receive-event-stream msg n])
      )
    (recur (inc n))))

(defn start-system [sistem & components]
  (swap! sistem component/update-system components component/start))

(defn initialize-system []
  (start-system sys :event-source)
  (swap! sys assoc-in [:event-source :listener-loop]
         (listener (get-in @sys [:event-source :msg-ch]))))

(defn- ^:export main []
  "To be executed on page load. The main point of entry for the app."
  (println "initializing the database")
  (bones.db/setup)
  (handlers/setup)
  (html/main)
  (initialize-system)
  )



(comment
  ;; creat message in db with :event/message  "hello world" :event/number 1
  (handlers/dispatch [:receive-event-stream "hello world" 1])

  ;; get current result of query - all :event/messages with :event/numeber less than 100
  @(handlers/subscribe [:event-stream-messages 100])

  )

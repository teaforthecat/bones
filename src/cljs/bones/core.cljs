(ns bones.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [bones.handlers :as handlers]
            [bones.system :as system]
            ;; [bones.html :as html]
            [bones.event-source :as es]
            [com.stuartsierra.component :as component]
            [reagent.core :as reagent]
            [cljs.core.async :as a]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))

;; todo: for development only
(enable-console-print!)

;; this should be configurable duh
(def config
  {:event-source-url "http://localhost:3000/api/events?topic=userspace.jobs-output"}
;;  {:event-source-url "ws://localhost:3000/api/ws?topic=userspace.jobs-output"}
  )


(def sys (system/system config))


(defn incoming-message-listener [msg-ch]
  (go-loop [n 0]
    (let [msg (<! msg-ch)]
      (handlers/dispatch [:receive-event-stream msg n]))
    (recur (inc n))))

(defn start-system [sistem & components]
  ;; this is a sad state of affairs (?)
  (if (nil? (get-in @sys [:command-listener :query-ratom]))
    (swap! sys assoc-in [:command-listener :query-ratom]
           (handlers/form-submit-listener)))
  (swap! sistem component/update-system components component/start))

(defn stop-system [sistem & components]
  (swap! sistem component/update-system components component/stop))

(defn initialize-system [app-type]
  (case app-type
    :om-next
    (do (println "not yet supported"))
    :re-frame
    (do
      ;; hack re-frame to use datascript
      (bones.db/setup)
      ;; register subs and handlers with re-frame
      (handlers/setup)
      ;; fixme: use system injection
      (swap! sys assoc-in [:event-source :listener-loop]
             (incoming-message-listener (get-in @sys [:event-source :msg-ch])))
      (if (handlers/logged-in?) ;; this will also happen on login
        (start-system sys :event-source :command-listener)))))

;; moved
;; (defn- ^:export main []
;;   "To be executed on page load. The main point of entry for the app."
;;   (println "initializing the database")
;;   (handlers/setup)
;;   (html/main)
;;   (initialize-system)
;;   )



(comment
  ;; creat message in db with :event/message  "hello world" :event/number 1
  (handlers/dispatch [:receive-event-stream {:input :input :output :output :job-sym :job-sym :uuid :uuid} 1])

  ;; get current result of query - all :event/messages with :event/numeber less than 100
  @(handlers/subscribe [:event-stream-messages 100])

  )

(ns bones.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [bones.handlers :as handlers]
            [bones.html :as html]
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
  {:event-source-url "http://localhost:3000/api/events?topic=userspace.jobs..wat-output"})

(defn command-dispatcher [ky atm old new]
  (let [{:keys [:bones.command/message
                :bones.command/uuid
                :db/id
                :bones.command/name]
         :as command-to-post} (ffirst new)]

    (if name ;;weird nil bug
      (let [result (handlers/post (str "http://localhost:3000/api/command/" name)
                                  {:message message})
            success (> 300 (:status result)) ])
      (handlers/dispatch [:update-command id (if success :received :error-sending)]))

    ))

(defrecord CommandListener [query-ratom]
  component/Lifecycle
  (start [cmp]
    (if (:listener cmp)
      (do
        (println "already listening")
        cmp)
      (do
        (if (:query-ratom cmp)
          (do
            (println "listening now")
            (assoc cmp :listener
                   (add-watch (:query-ratom cmp) :command-listener
                              command-dispatcher)))
          (do
            (println "query-ratom not defined")
            cmp))))
    )
  (stop [cmp]
    (if (:listener cmp)
      (do
        (println "no longer listening")
        (remove-watch (:listener cmp) :command-listener)
        (dissoc cmp :listener))
      (do
        (println "already not listening")
        cmp))))

(defn system [config]
  (reagent/atom (component/system-map
         :event-source (es/event-source (:event-source-url config)
                                        (a/chan))
         :command-listener (map->CommandListener {}))))

(def sys (system config))


(defn incoming-message-listener [msg-ch]
  (go-loop [n 0]
    (let [msg (<! msg-ch)]
      (handlers/dispatch [:receive-event-stream msg n])
      )
    (recur (inc n))))

(defn start-system [sistem & components]
  ;; this is a sad state of affairs (?)
  (if (nil? (get-in @sys [:command-listener :query-ratom]))
    (swap! sys assoc-in [:command-listener :query-ratom]
           (handlers/form-submit-listener)))
  (swap! sistem component/update-system components component/start))

(defn stop-system [sistem & components]
  (swap! sistem component/update-system components component/stop))

(defn initialize-system []
  ;; inject channel listener...hmmm stable?
  (swap! sys assoc-in [:event-source :listener-loop]
         (incoming-message-listener (get-in @sys [:event-source :msg-ch])))
  (if (handlers/logged-in?) ;; this will also happen on login
    (start-system sys :event-source :command-listener)))

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

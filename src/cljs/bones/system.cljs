(ns bones.system
  (:require [bones.event-source :as es]
            [bones.http :as http]
            [bones.handlers :as handlers]
            [com.stuartsierra.component :as component]
            [cljs.core.async :as a]
            [reagent.core :as reagent]))

(defn command-dispatcher [ky atm old new]
  (let [{:keys [:bones.command/message
                :bones.command/uuid
                :bones.command/url ;; FIXME one or the other
                :db/id
                :bones.command/name]
         :as command-to-post} (ffirst new)]
    (if name ;;fixme: weird nil bug
      (do
        (handlers/dispatch [:update-command id :waiting])
        (let [result (http/post (or url (str "http://localhost:3000/api/command/" name))
                                {:message message :uuid uuid})
              success (> 299 (:status result)) ]
          ;; perhaps use uuid instead of id ? for consistancy
          (handlers/dispatch [:update-command id (if success :received :error-sending)]))))
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
  "system-map wrapped in a reactive atom to allow changes to be shown to the user immediately"
  ;; TODO: make app-type independent with a regular atom
  (reagent/atom (component/system-map
         :event-source (es/event-source (:event-source-url config)
                                        (a/chan))
         :command-listener (map->CommandListener {}))))

(ns bones.handlers
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [schema.core :as s])
  (:require [bones.db :as db]
            [cljs.core.async :refer [<! take!]]
            [cljs-http.client :as http]
            [cljs-uuid-utils.core :as uuid]
            [reagent.core :as reagent]
            [re-frame.core]
            [goog.net.cookies]))

(def dispatch re-frame.core/dispatch)
(def subscribe re-frame.core/subscribe)

(re-frame.core/register-sub
 :connection-status
 (fn [db]
   (reaction
    (let [{:keys [:event-source :command-listener ]} @bones.core/sys]
      ;; connected if both event-stream exists and query-ratom exists
      (and (:stream event-source)
           (:listener command-listener))))))

(defn post [url data]
  (go
    (let [resp (<! (http/post url  {:edn-params data}))]
      resp)))

(comment
  (take!
   (post "http://localhost:3000/api/command/userspace.jobs..wat" {:message {:weight-kg 5
                                                                            :name "gold"}})
   println
   true)

  )


;; TODO create multimethod
(defn submit-form [form-ratom default-form]
  (go
    (let [{:keys [password username]} @form-ratom
          resp (<!
                (http/post "http://localhost:3000/login" {:edn-params
                                                          {:username username
                                                           :password password}}))]
      (let [success? (= 200 (:status resp))]
        (if success? ;;cookie stored
          (do
            (dispatch [:flash :success "Logged in successfully"])
            (bones.core/start-system bones.core/sys :event-source :command-listener)
            (reset! form-ratom default-form))
          (let [message (get-in resp [:body :message] "Something went wrong submitting the form")]
            ;; maybe report field level errors?
            (swap! form-ratom assoc :errors {:message message})))))))

(re-frame.core/register-handler
 :wat-button-clicked
 []
 (fn [app-db [_ special-thing weight-kg]]
  (take!
   (post "http://localhost:3000/api/command/userspace.jobs..wat" {:message {:weight-kg weight-kg
                                                                            :name special-thing}})
   println
   true)
   app-db))


(re-frame.core/register-handler
 :flash
 []
 (fn [app-db [_ type message]]
   (.log js/console type)
   (.log js/console message)
   app-db))

(defn logged-in? []
  (.containsKey goog.net.cookies "bones-session"))

(re-frame.core/register-handler
 :logout
 (fn [app-db [_ form]]
   ;; maybe let the server do it?
   ;; todo choose one or the other
   (.remove goog.net.cookies "bones-session")
   (bones.core/stop-system bones.core/sys :event-source :command-listener)
   (dispatch [:logout-token])
   (dispatch [:flash :success "Logged out"])
   app-db))

(re-frame.core/register-handler
 :submit-form
 []
 (fn [app-db [_ form-id form-ratom default-form]]
   (submit-form form-ratom default-form)
   app-db))

(defn logout-token [db token]
  [{:db/retract 100 :bones/token token}])

(defn login-token [db token]
  [{:db/id 100 :bones/token token}])

(defn inc-click-count [db]
  [[:db.fn/call db/attr-inc :click-count]])

;; hmm, positional args
(defn receive-event-stream [db [message msg-number]]
  "handle messages from the server - Server Sent Events"
  (let [{:keys [:uuid :input :output :job-sym]} message]
    (if uuid
      (dispatch [:update-command uuid :processed]))
    ;; both update command and record events - just cause ?
    ;; todo provide a function to react to events
    [{:db/id -1 :event/message message}
     {:db/id -1 :event/uuid uuid}
     {:db/id -1 :event/input input}
     {:db/id -1 :event/output output}
     {:db/id -1 :event/job-sym job-sym}
     {:db/id -1 :event/number msg-number}
     ]))

(defn update-command [db [uuid state & response-messages]]
  ;; or could query db, then create transaction
  (let [result (datascript.core/q '{:find [?e]
                                    :in [$ ?uuid]
                                    :where [[?e :bones.command/uuid ?uuid]]}
                                  db uuid)
        eid (or (ffirst result) -1)]

    [{:db/id eid :bones.command/state state}
     (if (not (empty? response-messages))
       {:db/id eid :bones.command/response-messages response-messages})]))

(def ui-q
  '{:find [(pull ?e [:ui.component/state])]
    :in [$ ?c-id]
    :where [[?e :ui.component/id ?c-id]]})

(def submitted-forms-q
  '{:find [(pull ?e
                 [:bones.command/name
                  :bones.command/message
                  :bones.command/errors
                  :bones.command/uuid
                  :bones.command/url
                  :bones.command/state
                  :db/id
                  ])]
    :in [$ ?state]
    :where [[?e :bones.command/state ?state]]
    })

;; TODO use record here
(def form-state-q
  '{:find [(pull ?e
                 [:bones.command/name
                  :bones.command/url
                  :bones.command/uuid
                  :bones.command/message
                  :bones.command/state
                  :db/id ;;choose one id or uuid
                  ])]
    :in [$ ?uuid]
    :where [[?e :bones.command/uuid ?uuid]]
    }  )

(def event-stream-messages-q
  '{:find [(pull ?e [:event/uuid :event/input :event/output :event/job-sym :event/number])]
    :in [$ ?max]
    :where [[?e :event/number ?number]
            [(< ?number ?max)]]
    })

(def reactive-queries
  {:get-login-token '[:find ?token
                      :where [100 :bones/token ?token]]
   :click-count '[:find ?v
                  :where [?e :click-count ?v]]
   :event-stream-messages-q event-stream-messages-q
   ;; :command-listener-q command-listener-q
   :submitted-forms-q submitted-forms-q
   :ui-q ui-q
   :form-state-q form-state-q
   }
  )

;; TODO use record here
(defn submit-form-tx [db [name uuid url message]]
  [{:db/id -1 :bones.command/name name}
   {:db/id -1 :bones.command/url url}
   {:db/id -1 :bones.command/uuid uuid}
   {:db/id -1 :bones.command/message message}
   {:db/id -1 :bones.command/state :submitted}])

(defn add-submit-form [db [command uuid url message]]
  [{:db/id -1 :bones.command/name command}
   {:db/id -1 :bones.command/url url}
   {:db/id -1 :bones.command/uuid uuid}
   {:db/id -1 :bones.command/message message}
   {:db/id -1 :bones.command/state :submitted}])

(defn add-errors-form [db [uuid errors]]
  (let [result (datascript.core/q '{:find [?e]
                                    :in [$ ?uuid]
                                    :where [[?e :bones.command/uuid ?uuid]]
                                    }
                                  db uuid)
        ;; todo raise error here instead
        eid (or (ffirst result) -1)]
    [{:db/id eid :bones.command/errors errors}
     {:db/id eid :bones.command/state :error}]))

(defn add-processed-form [db [uuid input output job-sym]]
  (let [result (datascript.core/q '{:find [?e]
                                    :in [$ ?uuid]
                                    :where [[?e :bones.command/uuid ?uuid]]
                                    }
                                  db uuid)
        ;; todo raise error here instead
        eid (or (ffirst result) -1)]
    [{:db/id eid :bones.command/state :processed}
     {:db/id eid :bones.command/input input}
     {:db/id eid :bones.command/output output}
     ;; {:db/id eid :bones.command/name job-sym} ;;shouldn't change
     ]))

(defn add-new-form [db [uuid]]
  [{:db/id -1 :bones.command/uuid uuid}
   {:db/id -1 :bones.command/state :new}])

(defn new-command-uuid []
  (uuid/make-random-uuid))

(defn new-form
  ([]
  (new-form {}))
  ([defaults]
   (let [uuid (uuid/make-random-uuid)
         tx-res (dispatch [:add-new-form uuid])]
     (reagent/atom (merge (or defaults {}) {:uuid uuid})))))

(defn command-form
  ([command]
  (command-form command {}))
  ([command defaults]
   (let [uuid (uuid/make-random-uuid)
         tx-res (dispatch [:add-new-form uuid])
         form (reagent/atom (merge (or defaults {}) {:uuid uuid}))]
     ;; {:lifecycle {

     ;;              ;;hmmmmmmmmm [{:keys [:ui.component/state]} (ffirst @activation)]
     ;;              :cancel #(do (dispatch [:ui uuid :hide])
     ;;                           ;;mmm
     ;;                           (reset! form (new-form defaults)))
     ;;              ;; :validate (bones/form-validator (command some-jobs) error_messages)
     ;;              ;; :submit (bones.forms/submit-function form command)
     ;;              }
     ;;  :form form
     ;;  }
     form))
 )


;; consider unified the q format
(defn ui [db [component-id state]]
  (let [result (datascript.core/q '{:find [?e]
                                    :in [$ ?c-id]
                                    :where [[?e :ui.component/id ?c-id]]}
                                  db component-id)
        eid (or (ffirst result) -1)]
    [{:db/id eid :ui.component/id component-id}
     {:db/id eid :ui.component/state state}]))

;; (defn command-listener [eid]
;;   (subscribe [:command-lister-q eid]))

(def mutations
  {:login-token login-token
   :logout-token logout-token
   :yes-button-clicked inc-click-count
   :receive-event-stream receive-event-stream
   :submit-form-tx submit-form-tx
   :add-submit-form add-submit-form
   :add-new-form add-new-form ;; hmm, seems to be a pattern here
   :add-errors-form add-errors-form
   :add-processed-form add-processed-form
   :update-command update-command
   :ui ui})

(defn setup []
  (mapv db/register-query reactive-queries)
  (mapv db/register-mutation mutations))

(defn form-submit-listener []
  ;;TODO somethingid
  (subscribe [:submitted-forms-q :submitted]))


(comment
  ;;(setup) ;;timeout
  (datascript.core/q (:event-stream-messages-q reactive-queries)
                     @re-frame.db/app-db 100)
  ;;(db/register-mutation [:ui ui])

  (datascript.core/q ui-q
                     @re-frame.db/app-db
                     :userspace.jobs/who)

  (ui @re-frame.db/app-db [:userspace.jobs/who :show])

  (def x-uuid (new-command-uuid))
  (dispatch [:add-submit-form x-uuid :userspace.jobs/wat {:weight-kg 15 :name "hux"} ])

  @(subscribe [:command-listener-q x-uuid])
  @(subscribe [:submitted-forms-q :submitted])

  )

(ns bones.handlers
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [schema.core :as s])
  (:require [bones.db :as db]
            [cljs.core.async :refer [<! take!]]
            [cljs-http.client :as http]
            [cljs-uuid-utils.core :as uuid]
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
      (let [success? (= 200 (:status resp))
            token (get-in resp [:body :token])]
        (if success?
          (do
            (dispatch [:login-token token])
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
  ;; todo choose one or the other
  (or
   (seq? (subscribe [:get-login-token]))
   (.containsKey goog.net.cookies "bones-session")))

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
  [{:db/id -1 :event/message message}
   {:db/id -1 :event/number msg-number}])

(defn update-command [db [id state]]
  ;; or could query db, then create transaction
  [{:db/id id :bones.command/state state}])

(def submitted-forms-q
  '{:find [(pull ?e
                 [:bones.command/name
                  :bones.command/message
                  :bones.command/errors
                  :bones.command/uuid
                  :bones.command/state
                  :db/id
                  ])]
    :in [$ ?state]
    :where [[?e :bones.command/state ?state]]
    })

(def reactive-queries
  {:get-login-token '[:find ?token
                      :where [100 :bones/token ?token]]
   :click-count '[:find ?v
                  :where [?e :click-count ?v]]
   :event-stream-messages '{:find [(pull ?e [:event/message :event/number])]
                            :in [$ ?max]
                            :where [[?e :event/number ?number]
                                    [(< ?number ?max)]]
                            }
   :command-listener-q command-listener-q
   :submitted-forms-q submitted-forms-q
   }
  )

(defn add-submit-form [db [uuid name message]]
  [{:db/id -1 :bones.command/name name}
   {:db/id -1 :bones.command/uuid uuid}
   {:db/id -1 :bones.command/message message}
   {:db/id -1 :bones.command/state :submitted}])


(defn command-listener [eid]
  (subscribe [:command-lister-q eid]))

(def mutations
  {:login-token login-token
   :logout-token logout-token
   :yes-button-clicked inc-click-count
   :receive-event-stream receive-event-stream
   :add-submit-form add-submit-form
   :update-command update-command})

(defn setup []
  (mapv db/register-query reactive-queries)
  (mapv db/register-mutation mutations))

(defn new-command-uuid []
  (uuid/make-random-uuid))

(defn form-submit-listener []
  ;;TODO somethingid
  (subscribe [:submitted-forms-q :submitted]))



(comment
  (setup)
  (datascript.core/q (:event-stream-messages reactive-queries)
                     @re-frame.db/app-db 100)

  (datascript.core/q submitted-forms-q
                     @re-frame.db/app-db)


  (def x-uuid (new-command-uuid))
  (dispatch [:add-submit-form x-uuid :userspace.jobs/wat {:weight-kg 15 :name "hux"} ])

  @(subscribe [:command-listener-q x-uuid])
  @(subscribe [:submitted-forms-q :submitted])

  )

(ns bones.handlers
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [schema.core :as s])
  (:require [bones.db :as db]
            [cljs.core.async :refer [<! take!]]
            [cljs-http.client :as http]
            [re-frame.core]
            [goog.net.cookies]))

(def dispatch re-frame.core/dispatch)
(def subscribe re-frame.core/subscribe)

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
            (reset! form-ratom default-form))
          (let [message (get-in resp [:body :message] "Something went wrong submitting the form")]
            ;; maybe report field level errors?
            (swap! form-ratom assoc :errors {:message message})))))))

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

(defn submit-form-tx [db name form]
  [{:db/id -1 :form/name name}
   {:db/id -1 :form/submitted true}])

(def reactive-queries
  {:get-login-token '[:find ?token
                      :where [100 :bones/token ?token]]
   :click-count '[:find ?v
                  :where [?e :click-count ?v]]})

(def mutations
  {:login-token login-token
   :logout-token logout-token
   :yes-button-clicked inc-click-count})

(defn setup []
  (mapv db/register-query reactive-queries)
  (mapv db/register-mutation mutations))

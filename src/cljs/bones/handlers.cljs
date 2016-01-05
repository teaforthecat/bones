(ns bones.handlers
  (:require [bones.db :as db]
            [re-frame.core]))

(def dispatch re-frame.core/dispatch)
(def subscribe re-frame.core/subscribe)

(defn login-token [db token]
  [{:db/id 100 :bones/token token}])

(def reactive-queries
  {:get-login-token '[:find ?token
                      :where [100 :bones/token ?token]]})

(def mutations
  {:login-token login-token})

(defn setup []
  (mapv db/register-query reactive-queries)
  (mapv db/register-mutation mutations))

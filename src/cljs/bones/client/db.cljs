(ns bones.client.db
  (:require [datascript.core :as d]
            [petrol.core :as petrol]))

;; double atomic, because petrol says swap!, and that won't work for datascript
;; so (d/q) has to use a double deref @@ to get at the db
(def conn (atom (d/create-conn {})))

(defn query [q & given]
  (apply d/q q @@conn given))

(defn transact! [db transactions]
  (d/transact! db transactions))

(defprotocol Transact
  (tx [transaction]))

(defrecord Transaction [event]
  petrol/Message
  (process-message [this conn]
    ;; todo come up with coercer
    (let [{:keys [event]} this
          tx-data (if (satisfies? Transact event)
                    (tx event)
                    event)]
      ;; force one tx map for now
      (transact! conn [tx-data]))
    conn))



(comment
  (d/q '[:find ?count :where [42 :counter ?count]] @@conn)

  (d/q '[:find [(pull ?e [:counter])] :where [?e :counter ?count]] @@conn)

  (d/q '[:find [(pull ?e [*])] :where [?e :counter ?count]] @@conn)

  (d/q '[:find ?count
       :in $ ?button-id
       :where [?button-id :counter ?count]] @@conn 42)

  )

;; maybe ???
;; (defn pull-all [where]
;;   (d/q '[:find [(pull ?e [*])] :where ,where ] @@conn))

;; (defn pull-one [where]
;;   (ffirst (pull-all where)))

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

(defprotocol React
  (q [this] "returns a tuple, datascript query, and givens"))

(defn unique-key [& args]
  ;; cheap hack
  ;; this way the query itself can be used instead of storing a uuid
  ;; this could get out of hand though with large queries
  (apply print-str args))

(defn bind [q given state-atom tx-pred]
  ;; tx-pred could be a function checking the new datums as a performance optimization
  (d/listen! @conn (unique-key q given)
             (fn [tx-report]
               (let [new-result (apply d/q q (:db-after tx-report) given)]
                 (when (not= new-result @state-atom)
                   (reset! state-atom new-result))))))

(defn unbind [q given]
  (d/unlisten! @conn (unique-key q given)))

(defrecord Reaction [question state-atom tx-pred]
  IDeref
  (-deref [this]
    (let [[q & given] (if (satisfies? React question)
                        (q question)
                        question)
          res (apply query q given)
          target (or (:state-atom this) (atom #{}))]
      (reset! target res)
      (bind q given target tx-pred)
      target)))

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

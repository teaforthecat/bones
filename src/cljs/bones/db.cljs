(ns bones.db
  (:require [datascript.core :as d]
            [cljs-uuid-utils.core :as uuid]
            [reagent.core :as reagent]
            [re-frame.core]
            ))
;; taken from
;; TODO: give credit / MIT License
;; https://github.com/rmoehn/theatralia/blob/devel/src/cljs/theatralia/thomsky.cljs

(defn attr-inc [db attr]
  "increment some attribute, or create it - only one entity will be used
 - kinda weird - just be carefull"
  (let [[eid val] (first (d/q
                          '{:find [?e ?v]
                            :in  [$ ?attr]
                            :where [[?e ?attr ?v]]}
                          db attr))]
    (if eid
      [{:db/id eid attr (inc val)}]
      [{:db/id -1  attr 1}])))


(defn set-up-datascript!
  "Swaps in a Datascript database with schema ?SCHEMA (default: Datascript
  default schema) for the APP-DB in re-frame. To be used as an event handler."
  ([app-db [_ & [?schema]]]
   (let [conn (d/create-conn ?schema)]
     (reset! app-db @conn)
     (reset-meta! app-db (meta conn)))))


(defn bind
  "Returns a ratom containing the result of query Q with the arguments Q-ARGS on
  the value of the database behind CONN."
  [q conn & q-args]
  (println "bind called" q) ; Commented out on purpose. – See note above.
  (println "bind called" conn) ; Commented out on purpose. – See note above.
  (println "bind called" q-args) ; Commented out on purpose. – See note above.
  (let [k (uuid/make-random-uuid)
        res (apply d/q q @conn q-args)
        state (reagent/atom res)]
    ;; (reset! state res)
    (d/listen! conn k
               (fn [tx-report]
                 (println tx-report)
                 (let [new-result (apply d/q q (:db-after tx-report) q-args)]
                   (when (not= new-result @state)
                     (reset! state new-result)))))
    (set! (.-__key state) k)
    state))

(defn unbind
  "Stops changes in CONN from causing updates of ratom STATE."
  [conn state]
  (d/unlisten! conn (.-__key state)))

(defn pure-datascript
  "opposed to re-frame's 'pure', where the HANDLER has to return a whole
  new app state, here the HANDLER only returns transaction data that will be
  used to change the app state."
  [handler]
  (fn pure-datascript-handler [conn event-vec]
    (assert (satisfies? cljs.core/IAtom conn)
            (str "conn has to be an atom. Got: " conn))
    (let [db @conn
          _ (assert (satisfies? datascript.db/IDB db)
                    (str "@conn has to be a Datascript Database. Got: " db))
          txd (handler db event-vec)]
      (assert (sequential? txd)
              (str "Handler has to return sequence of transaction data."
                   "Got: " txd))
      ;; the value returned is not important because it is the last ran middleware
      (d/transact! conn txd))))




(defn register-datascript-handler
  "use the pure-datascript and trim-v middleware"
  ([id handler]
   (register-datascript-handler id [] handler))
  ([id middleware handler]
   (re-frame.handlers/register-base id
                                    ;; middleware is composed right to left
                                    ;; pure-datascript runs transact! - execution stops there
                                    [pure-datascript middleware re-frame.middleware/trim-v]
                                    handler)))
(defn setup []
  (re-frame.handlers/register-base
   :initialize []
   set-up-datascript!)
  (re-frame.core/dispatch-sync [:initialize])
  @re-frame.db/app-db)

(defn register-query [name-query]
  "listen for changes with (subscribe [:x :y :z])"
  (let [[name query ] name-query]
    (re-frame.core/register-sub name (partial bind query))))

(defn register-mutation [name-handler]
  "effect the mutation with (dispatch [:x :y :z])"
  (let [[name handler] name-handler]
    (register-datascript-handler name handler)))

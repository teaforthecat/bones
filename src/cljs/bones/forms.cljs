(ns bones.forms
  (:require [automat.fsm]
            [automat.core :as a]
            [reagent.core :as r]
            [cljs-uuid-utils.core :as uuid]
            [bones.handlers :refer [dispatch subscribe]]))

(def automata (a/or [:hidden (a/$ :hidden) :new (a/$ :new) :cancel (a/$ :cancel) (a/$ :hidden)]
                    [:hidden (a/$ :hidden) :new (a/$ :new)
                     (a/or [:submitted (a/$ :submitted)] [:cancel (a/$ :cancel)])
                     (a/or [:waiting (a/$ :waiting)]     [:cancel (a/$ :cancel)])
                     (a/or [:received (a/$ :received)]
                           [:cancel (a/$ :cancel)]
                           [:error-sending (a/$ :error-sending)])
                     (a/or [:waiting (a/$ :waiting)]
                           [:processed (a/$ :processed)]
                           [:cancel (a/$ :cancel)])
                     (a/or [:waiting (a/$ :waiting)]     [:cancel (a/$ :cancel)])
                     (a/$ :hidden)]))

;; todo use a multi-method instead
;; todo generalize (:action input)
(def actions
  {:reducers {:hidden (fn [cur input]
                        (println cur input)
                        (assoc cur :state (:action input)))
              :new (fn [cur input]
                     ;;(println cur input )
                     (let [uuid (uuid/make-random-uuid)
                           response-reaction (subscribe [:form-state-q uuid])]
                       ;; todo tear down watcher somehow (?)
                       (-> cur
                           (assoc :uuid uuid)
                           (assoc :response-reaction response-reaction)
                           (assoc :state (:action input)))))
              :submitted  (fn [cur input]
                            ;; (println cur input)
                            (let [{:keys [message url]} input
                                  {:keys [uuid command]} cur]
                              (dispatch [:submit-form-tx command uuid url message])
                              )
                            (assoc cur :state (:action input)))
              :cancel  (fn [cur input]
                         ;; (println cur input )
                         ;; it is important that it doesn't matter which goes first
                         ;;   - this cancel action or :hidden
                         ;; will be reset on :new
                         (if-let [response-reaction (get-in cur [:value :response-reaction])]
                           (do
                             (println "unwatching response-reaction")
                             (remove-watch response-reaction :response-dispatcher)))
                         (-> cur
                             (dissoc :uuid)
                             (assoc :state (:action input)))
                         )
              :error-sending (fn [cur input] (println cur input ) (assoc cur :state "error-sending"))
              :waiting  (fn [cur input]
                          ;;(println cur input )
                          (assoc cur :state "waiting"))
              :received  (fn [cur input]
                           ;;(println cur input )
                           (assoc cur :state "received"))
              :processed  (fn [cur input]
                            ;; (println cur input )
                            (dispatch [:flash :success (:command cur)])
                            ;; will be reset on :new
                            (assoc cur :state (:action input)))
              }
   :signal :action })

(def form-fsm (a/compile automata actions))

(def advance (partial a/advance form-fsm))

(defn new-form [{:keys [defaults url command] :or {}}]
  (let [default-fields (r/atom (or defaults {}))
        form (r/atom {:errors {}
                      :fields default-fields})
        form-fsm (advance {:command command} {:action :hidden})
        ;; for error handling, to get back to base
        new-form-state (advance form-fsm {:action :new})]


    ;; new-fn has a fallback parameter so a new form will be generated,
    ;; basically starting over, because I don't know how to cycle through a lifecycle
    (swap! form merge {:submit-fn #(swap! form update :fsm advance {:action :submitted :message % :url url})
                       :cancel-fn #(swap! form update :fsm advance {:action :cancel})
                       :new-fn    #(do
                                     ;; a new one of the above
                                     (swap! form assoc :fields (r/atom (or defaults {})))
                                     (swap! form update :fsm advance {:action :new} new-form-state)
                                     ;; all this just to get the form to say ":received" from the http post
                                     (add-watch (get-in @form [:fsm :value :response-reaction])
                                                :response-dispatcher
                                                (fn [ky atm old new]
                                                  (let [{:keys [:bones.command/state]} (ffirst new)]
                                                    (if (some #{state} #{:waiting :received :error-sending :processed})
                                                      ;; only advance based on dispatches from CommandListener
                                                      (swap! form update :fsm advance {:action state})))))
                                     )
                       :fsm form-fsm})

    form
    ))




(comment

  (def f  (new-form {}))
  (get-in @f [:fsm :value :state]) ;;=> :hidden
  ((:new-fn @f))
  (get-in @f [:fsm :value :state]) ;;=> :new
  ((:cancel-fn @f)) ;;=>
  (get-in @f [:fsm :value :state]) ;;=> :cancel
  ((:new-fn @f))
  (get-in @f [:fsm :value :state]) ;;=> :new
  (:fsm @f)

  )

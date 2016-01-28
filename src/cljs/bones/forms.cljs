(ns bones.forms
  (:require [automat.fsm]
            [automat.core :as a]
            [reagent.core :as r]
            [cljs-uuid-utils.core :as uuid]
            [bones.handlers :refer [dispatch]]))

(def automata (a/or [:hidden (a/$ :hidden) :new (a/$ :new) :cancel (a/$ :cancel) (a/$ :hidden)]
                    [:hidden (a/$ :hidden) :new (a/$ :new) :submitted (a/$ :submitted)
                     :waiting (a/$ :waiting) :received (a/$ :received)
                     :waiting (a/$ :waiting) :processed (a/$ :processed) (a/$ :hidden)]))

(def actions
  {:reducers {:hidden (fn [cur input]
                        (println cur input)
                        (assoc cur :state (:action input)))
              :new (fn [cur input]
                     (println cur input )
                     (-> cur
                      (assoc :uuid (uuid/make-random-uuid))
                      (assoc :state (:action input))))
              :submitted  (fn [cur input]
                            (println cur input)
                            ;; {:state :new,
                            ;;  :command nil,
                            ;;  :uuid #uuid "8f2d73d0-1744-4983-8b62-050280f5e2ca"}
                            ;; {:action :submitted,
                            ;;  :message {:role user,
                            ;;            :name aoeu},
                            ;;  :url http://localhost:3000/api/commands/userspace.jobs..who}
                            (let [{:keys [message url]} input
                                  {:keys [uuid command]} cur]
                              (dispatch [:submit-form-tx command uuid url message])
                              )
                            (assoc cur :state (:action input)))
              :cancel  (fn [cur input]
                         (println cur input )
                         ;; it is important that it doesn't matter which goes first
                         ;; this cancel action or :hidden
                         ;;(assoc cur :state (:action input))
                         (assoc cur :state (:action input))
                         )
              :waiting  (fn [cur input] (println cur input ) (assoc cur :state "waiting"))
              :received  (fn [cur input] (println cur input ) (assoc cur :state "received"))
              :processed  (fn [cur input] (println cur input ) (assoc cur :state "processed"))
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
                                     (swap! form update :fsm advance {:action :new} new-form-state))
                       :fsm form-fsm})

    form))

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

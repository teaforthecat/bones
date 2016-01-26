(ns bones.forms
  (:require [automat.fsm]
            [automat.core :as a]
            [reagent.core :as r]))




;; (def form-states
;;   {nil new-form
;;    :submitted #(and (dispatch [:make-request]) :waiting-received)
;;    :waiting-received #(and (dispatch [:ui :received]) :received)
;;    :received :waiting-processed
;;    :waiting-processed
;;    }
;;   )


(defn actionify [states]
  (interleave states (mapv a/$ states)))


;; (def automata (a/or (actionify [:hidden :new :cancel (a/$ :hidden)])
;;                     (actionify [:hidden :new :submitted :waiting :received :waiting :processed :hidden])))

(def automata (a/or [:hidden (a/$ :hidden) :new (a/$ :new) :cancel (a/$ :cancel) (a/$ :hidden)]
                    [:hidden (a/$ :hidden) :new (a/$ :new) :submitted (a/$ :submitted)
                     :waiting (a/$ :waiting) :received (a/$ :received)
                     :waiting  (a/$ :waiting) :processed  (a/$ :processed) (a/$ :hidden)]))


(def actions
  {:reducers {:hidden (fn [cur input]
                        (println cur input)
                        (assoc cur :state (:action input)))
              :new (fn [cur input]
                     (println cur input )
                     (assoc cur :state (:action input)))
              :submitted  (fn [cur input] (println cur input )
                            (println cur input )
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

(def advance (partial a/advance  form-fsm))

(defn new-form [{:keys [defaults] :or {}}]
  (let [form (r/atom {:defaults defaults
                      :errors {}
                      :fields (r/atom {})})
        form-fsm (advance {} {:action :hidden})]

    ;; new-fn has a fallback parameter so a new form will be generated,
    ;; basically starting over, because I don't know how to cycle through a lifecycle
    (swap! form merge {:submit-fn #(swap! form update :fsm advance {:action :submitted :input %})
                       :cancel-fn #(swap! form update :fsm advance {:action :cancel})
                       :new-fn    #(swap! form update :fsm advance {:action :new} (advance form-fsm {:action :new}))
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

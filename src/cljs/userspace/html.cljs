(ns userspace.html
  (:require [userspace.jobs-conf :refer [some-jobs]]
            [reagent.core :as reagent]
            [reagent-forms.core :refer [bind-fields]]
            [bones.handlers :as handlers]
            [schema.core :as s]
            [bones.re-frame.html :as bones]
            [re-frame.core :refer [subscribe dispatch]]))

;; for demo only
(defn yes-button []
  [:div  {:class "button-class"
          :on-click  #(dispatch [:yes-button-clicked])}
   "Yes"])

(defn message-li [{:keys [:event/message :event/number]}]
  [:li.row
   [:div.message message]
   [:div.number number]
   ])

(defn messages-received []
  (let [messages (handlers/subscribe [:event-stream-messages 100])]
    (fn []
      ;; not fortunately the query is returning a list of lists
      (let [res @messages]
        (into [:ul.messages] (map message-li (flatten res)))))))

;; for demo only
(defn click-count []
  (let [cnt (subscribe [:click-count])]
    (fn []
      [:div.count
       [:span @cnt]])))

;; for demo only
(defn wat-button [special-thing weight-kg]
  [:div  {:class "button-class"
          ;; :on-click  #(dispatch [:wat-button-clicked special-thing @weight-kg])}
          :on-click  #(dispatch [:add-submit-form
                                 #uuid"9c87fafa-671c-4fdd-b543-306661f051c0"
                                 "userspace.jobs..wat"
                                 {:weight-kg @weight-kg :name special-thing}])}
   (str "wat " special-thing)])

(def weight (reagent/atom 17))

(defn weight-form []
  [:div
   [:div
    [:label (str "Weight:  " @weight " kg")]]
   [:div
    [:input {:id :weight
             :type :range
             :value @weight
             :on-change #(reset! weight (-> % .-target .-value int))} ]]]
  )


;; (defn connected-status []
;;   (let [status (subscribe [:connection-status])]
;;     (fn []
;;       [:div.connection-status
;;        (if @status
;;          [:h2.connected "connected"]
;;          [:h2.not-connected "not-connected"])])))

;; careful, copied from bones.jobs/
(defn sym-to-topic [^clojure.lang.Keyword job-sym]
  (-> (str job-sym)
      (clojure.string/replace "/" "..") ;; / is illegal in kafka topic name
      (subs 1))) ;; remove leading colon


(defn submit-function [form command]
  (fn []
    (let [uuid (:uuid @form)
          message (select-keys @form (keys (command some-jobs)))
          errors (s/check (command some-jobs) message)
          command-path (sym-to-topic command)]
      (println message)
      (println (type (:weight-kg message)))
      (println (type (:weight-kg @form)))
      (println errors)
      (if errors
        (dispatch [:add-errors-form uuid errors])
        (dispatch [:add-submit-form uuid command-path message])))))

(defn form-for
  ([command error_messages]
   (form-for command error_messages {}))
  ([command error_messages defaults]
   (let [form (handlers/new-form defaults)
         validator (bones/form-validator (command some-jobs) error_messages)
         cancel-fn #(dispatch [:ui command :hide])
         reset-form #(reset! form (handlers/new-form defaults))
         submit-fn (submit-function form command)]
     (fn []
       [bind-fields
        [:div.fieldset
         [:label.control-label {:for :weight-kg
                                :field :label
                                :id :weight-kg
                                :preamble "Weight: "
                                :postamble " kg"}]
         (bones/bootstrap-field-with-feedback :weight-kg "" validator :type :range :field :range)
         (bones/bootstrap-field-with-feedback :name "Name" validator)
         [:button {:on-click submit-fn} "Submit" ]
         [:button {:on-click #(do (reset-form) (cancel-fn))} "Cancel" ]
         ]
        form]))))

;; todo: send the uuid around the world

(defn toggled-form [ui-q form non-form]
  (let [activation (subscribe ui-q)]
    (fn []
      (if (= :show (:ui.component/state (ffirst @activation)))
        form
        non-form))))

(defn layout []
  [:div.layout
   [bones/connected-status]
   [bones/login-form]
   [yes-button]
   [click-count]
   [weight-form]
   [wat-button "book" weight]
   [toggled-form [:ui :userspace.jobs/wat]
    [form-for :userspace.jobs/wat {:weight-kg {:aria "Must be a Number"}} {:weight-kg 0}]
    [:button {:on-click #(dispatch [:ui :userspace.jobs/wat :show])} "Add wat" ]]
   [messages-received]])

(defn main []
  (reagent/render-component layout (.getElementById js/document "app")))

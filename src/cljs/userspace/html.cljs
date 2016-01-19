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

(defn message-li [{:keys [:event/uuid :event/input :event/output :event/job-sym :event/number]}]
  [:li.row
   [:div.input (str "input: " input)][:br]
   [:div.output (str "output: " output)][:br]
   [:div.number (str "job-sym: " job-sym)][:br]
   [:div.number (str "number: " number)][:br]
   [:div.uuid (str "uuid: " uuid)][:br]
   ])

(defn messages-received []
  (let [messages (handlers/subscribe [:event-stream-messages-q 100])]
    (fn []
      (let [res @messages]
        ;; this is the incoming event processor (?wat?)
        ;; ((list of lists))
        (let [{:keys [:event/uuid :event/input :event/output :event/job-sym :event/number] :as message} (last (last res))]
          (if message
            (do
              (dispatch [:add-processed-form uuid input output job-sym])
              ;; this will close the form
              (dispatch [:ui job-sym :processed]))
            )
          )
        (into [:ul.messages] (map message-li (flatten res)))))))

;; for demo only
(defn click-count []
  (let [cnt (subscribe [:click-count])]
    (fn []
      [:div.count
       [:span @cnt]])))


;; copied from bones.jobs.clj
(defn sym-to-topic [job-sym]
  (-> (str job-sym)
      (clojure.string/replace "/" "..") ;; / is illegal in kafka topic name
      (subs 1))) ;; remove leading colon


(defn submit-function [form command]
  (fn []
    (let [uuid (:uuid @form)
          message (select-keys @form (keys (command some-jobs)))
          errors (s/check (command some-jobs) message)
          command-path (sym-to-topic command)]
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
      ;; query returns a list of lists
      (let [{:keys [:ui.component/state]} (ffirst @activation)]
        [:div.ui
         [:div.debug (str state)]
         (if (= :show state)
           form
           non-form)]))))

(defn layout []
  [:div.layout
   [bones/connected-status]
   [bones/login-form]
   [yes-button]
   [click-count]
   [toggled-form [:ui-q :userspace.jobs/wat]
    [form-for :userspace.jobs/wat {:weight-kg {:aria "Must be a Number"}} {:weight-kg 0}]
    [:button {:on-click #(dispatch [:ui :userspace.jobs/wat :show])} "Add wat" ]]
   [messages-received]])

(defn main []
  (reagent/render-component layout (.getElementById js/document "app")))

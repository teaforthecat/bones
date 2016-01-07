(ns bones.html
  (:require [reagent.core :as reagent]
            [reagent-forms.core :refer [bind-fields]]
            [bones.handlers :as handlers] ;; todo move logged-in?
            [re-frame.core :refer [subscribe dispatch]]))

;; for demo only
(defn yes-button []
  [:div  {:class "button-class"
          :on-click  #(dispatch [:yes-button-clicked])}
   "Yes"])

;; for demo only
(defn click-count []
  (let [cnt (subscribe [:click-count])]
    (fn []
      [:div.count
       [:span @cnt]])))

(defn validator [v-fn]
  "takes a fn that can return true to set the form's :valid? flag and therefore enable the submit button
or the fn can return a map with :errors which will be assoc'd to the form as :errors
or nothing will be done to the form"
  (fn [id value doc]
    (let [res (v-fn id value doc)]
      (if (= res true)
        (assoc doc :valid? true)
        (if (and (map? res) (:errors res) )
          (-> doc
              (assoc :errors (:errors res))
              (assoc :valid? false))
          (assoc doc :valid? false))))))

(defn submit [label form-id form-ratom default-form]
  [:button {:on-click #(dispatch [:submit-form form-id form-ratom default-form])
            :disabled (if-not (:valid? @form-ratom) "disabled" )}
   label])

(defn cancel [label form-ratom default-form]
  [:button {:on-click #(reset! form-ratom default-form)}
   "Cancel"])

(defn errors [label form-ratom]
  [:div.errors
   (if-let [message (get-in @form-ratom [:errors :message])]
     [:div
      [:span.label label]
      [:span.message message]])])

(defn logout [label form-ratom]
  [:button {:on-click #(dispatch [:logout form-ratom])}
   label])

(defn login [label form-ratom]
  [:button {:on-click #(swap! form-ratom assoc :enabled? true)}
   label])

(def login-fields
  [:div
   [:label "Username"]
   [:input {:id :username :field :text}]
   [:label "Password"]
   [:input {:id :password :type :password :field :text}]])

(defn login-validator [id value {:keys [username password] :as doc}]
  (let [valid (fn [s] (< 3 (count (str s))))]
    (and (valid username)
         (valid password))))

(defn login-form []
  (let [logged-in? (subscribe [:get-login-token])
        default-form {:enabled? false}
        form (reagent/atom {})]
    (fn []
      (if (:enabled? @form)
        [:div.form
         [:h3 "Login"]
         [errors "Errors" form]
         [bind-fields
          login-fields
          form
          (validator login-validator)]
         [cancel "Cancel" form default-form]
         [submit "Submit" :login form default-form] ;;maybe add default-form?
         ]
        (if (handlers/logged-in?)
          [logout "Logout" form default-form]
          [login "Login" form])))))

(defn layout []
  [:div.layout
   [login-form]
   [yes-button]
   [click-count]])

(defn main []
  (reagent/render-component layout (.getElementById js/document "app")))

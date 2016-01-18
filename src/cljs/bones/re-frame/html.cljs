(ns bones.re-frame.html
  (:require-macros [reagent-forms.macros :refer [render-element]])
  (:require [bones.handlers :as handlers]
            [schema.core :as s]
            [reagent.ratom :as ratom]
            [reagent.core :as reagent]
            [reagent-forms.core :refer [bind-fields bind init-field]]
            [re-frame.core :refer [subscribe dispatch]]))

(defn connected-status []
  (let [status (subscribe [:connection-status])]
    (fn []
      [:div.connection-status
       (if @status
         [:h2.connected "connected"]
         [:h2.not-connected "not-connected"])])))

(defn submit [label form-id form-ratom default-form]
  [:button {:on-click #(dispatch [:submit-form form-id form-ratom default-form])
            :disabled (if-not (empty? (:errors @form-ratom)) "disabled")}
   label])

(defn cancel [label form-ratom default-form]
  [:button {:on-click #(reset! form-ratom default-form)}
   "Cancel"])

(defn logout [label form-ratom]
  [:button {:on-click #(dispatch [:logout form-ratom])}
   label])

(defn login [label form-ratom]
  [:button {:on-click #(swap! form-ratom assoc :enabled? true)}
   label])


;;example:
;; (s/optional-key :email) (s/maybe
;;                             (s/pred #(string? (re-matches #".+@.+\..+" %)) :valid-email))

(def validators
  {:username s/Str
   :password (s/pred #(< 3 (count %)))
   })

(def error_messages
  {:email {:container "has-error"
           :glyphicon "glyphicon-remove"
           :aria      "Email is invalid"}
   :password {:container "has-error"
              :aria "Password is too short"}})

(defn form-validator [validators error_messages]
  (fn [doc]
    ;; this can be use both two ways
    ;; "doc" for a `bind-fields' event processor
    ;; "@doc" for reagent-form container `:valid?' validator
    (let [real-doc (if (= PersistentArrayMap (type doc)) doc @doc)
          result (s/check validators real-doc)]
      (if (or (nil? result) (instance? PersistentArrayMap result))
        (assoc doc :errors (select-keys error_messages (keys result)))
        (throw (ex-info "invalid schema" {:validators validators}))))))

(defn bootstrap-field-with-feedback [id label valid-fn & {:as opts}]
  (let [aria-id [:errors id :aria]
        validation #(get-in (valid-fn %) [:errors id])]
    [:div.form-group.has-feedback {:field :container
                                   :valid? #(:container (validation %))}

     ;; the label can change
     [:label.control-label {:for id
                            :placeholder label
                            :id [:errors id :label]
                            :field :label}]
     ;; the input finally
     [:input.form-control {:id id
                           :aria-describedby aria-id
                           :type (or (:type opts) :text)
                           :field (or (:field opts) :text)}]
     ;; use container to change class
     [:span.glyphicon.form-control-feedback {:field :container
                                             :valid?  #(:glyphicon (validation %))
                                             :aria-hidden true}]
     ;; use label to change content
     [:span.sr-only {:id aria-id
                     :field :label}]
     ]))

;; maybe
;; (defn straight-up-posting [url default-form & {:as opts}]
;;   (fn [form]
;;     (let [response (http/post url @form)
;;           {:keys [ok-msg error-msg]} opts]
;;       (if (> 299 (:status response))
;;         (do
;;           (if ok-msg (dispatch [:flash :success ok-msg]))
;;           (reset! form default-form))
;;         (do
;;           (if error-msg (dispatch [:flash :error error-msg]))
;;           (swap! form assoc :errors {:message (get-in response [:body :message])})
;;           )))))

(defn login-form [login-url]
  (let [default-form {:enabled? false}
        form (reagent/atom {})
        ;;maybe submission (straight-up-posting login-url default-form)
        validator (form-validator validators error_messages)]
    (fn []
      (if (:enabled? @form)
        [:div.form
         [:h3 "Login"]
         [bind-fields
          [:div.fields
           (bootstrap-field-with-feedback :username "Username" validator)
           (bootstrap-field-with-feedback :password "Password" validator :type :password)]
          form
          (fn [id value doc]
            (println doc)
            (validator doc))]
         [cancel "Cancel" form default-form]
         [submit "Submit" :login form default-form]
         ]
        (if (handlers/logged-in?)
          [logout "Logout" form default-form]
          [login "Login" form])))))

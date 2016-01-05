(ns bones.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [bones.handlers :as handlers]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]))


(enable-console-print!)

(comment
  (go
    (let [resp (<!
                (http/post "http://localhost:3000/login" {:edn-params
                                                          {:username "jerry"
                                                           :password "jerry"}}))]
      (let [success? (= 200 (:status resp))
            token (get-in resp [:body :token])]
        (if success?
          (handlers/dispatch [:login-token token])
          (println resp)))))
  ;; the token:
  (first (ffirst @(re-frame.core/subscribe [:get-login-token])))

  )


(defn- ^:export main []
  (println "initializing the database")
  (bones.db/setup)
  (handlers/setup))

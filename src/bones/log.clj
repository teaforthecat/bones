(ns bones.log
  (:require [taoensso.timbre :as log]))

;; config...


(defn wrap-with-body-logger
  "Returns a Ring middleware handler that will log the bodies of any
  incoming requests by reading them into memory, logging them, and
  then putting them back into a new InputStream for other handlers to
  read.
  This is inefficient, and should only be used for debugging."
  [handler]
  (fn [request]
    (log/info "request: " (dissoc request :body))
    (log/info "request-body: " (:body request))
    (handler request)))

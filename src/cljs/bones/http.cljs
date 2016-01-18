(ns bones.http
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]))

(defn post
  [url data]
  (go
    (let [resp (<! (http/post url  {:edn-params data}))]
      resp)))

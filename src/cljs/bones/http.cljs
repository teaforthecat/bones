(ns bones.http
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as c]
            [cljs.core.async :refer [<! take!]]))

(defn post
  [url data]
  (go
    (let [resp (<! (c/post url  {:edn-params data}))]
      resp)))

(defn get
  [url data]
  (go
    (let [resp (<! (c/get url  {:query-params data}))]
      resp)))

(comment
  (take!
   (get "http://localhost:3000/api/query" {:limit 5 :start 0 :index "wat" :q "*:*"})
   println
   true)



  )

(ns userspace.core
  (:require [userspace.job-conf :refer [some-jobs]]
            [userspace.html :as html]
            [bones.core :as bones]))

;some-jobs


(defn- ^:export main []
  "To be executed on page load. The main point of entry for the app."
  ;; order is important here
  (bones/initialize-system :re-frame)
  (html/main)
  )

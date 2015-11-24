(ns bones.core
  (:require [yada.yada :refer [yada] :as yada]
            [bidi.ring :refer [make-handler] :as bidi]
            [aleph.http :refer [start-server]]))

(def hello
  (yada "Hello World!\n"))

;; var ref for reloadability
(def routes ["/" #'hello] )

(def handler
  (bidi/make-handler routes))

#_(def system (bones.system/system {:env :dev}))

#_(alter-var-root #'system com.stuartsierra.component/start)

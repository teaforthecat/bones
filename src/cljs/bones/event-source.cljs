(ns bones.event-source
  (:require [com.stuartsierra.component :as component]))


(defrecord EventSource [stream state url]
  component/Lifecycle
  (start [cmp]
    (if (:stream cmp)
      (do
        (println "already started stream")
        cmp)
      (do
        (let [src (js/EventSource. (:url cmp) #js{ :withCredentials true } )]
          (println "starting stream")
          (.log js/console src)
          (aset src "onerror" (fn [e] (.log js/console e)))
          (aset src "onopen" (fn [e] (.log js/console e)))
          (aset src "onmessage" (fn [e] (.log js/console e)))

          (-> cmp
              (assoc :stream src)
              (assoc :state (.-readyState src))
              )))))
  (stop [cmp]
    (if (:stream cmp)
      (do
        (println "closing stream")
        (.close (:stream cmp))
        (dissoc cmp :stream))
      (do
        (println "stream already closed")
        cmp))))

(defn event-source [url]
  (map->EventSource {:url url}))

(comment
  (def es (event-source "http://localhost:3000/api/events?topic=userspace.jobs..wat-output"))
  (component/start es)
  (:stream es)
  (component/stop es)
  )

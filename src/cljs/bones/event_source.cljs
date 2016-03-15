(ns bones.event-source
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [schema.core :as s])
  (:require [com.stuartsierra.component :as component]
            [cljs.core.async :as a]
            [cljs-http.client :as http]
            [chord.client :refer [ws-ch]]))
;; this could move
(defn post [url data]
  (go
    (let [resp (<! (http/post url  {:edn-params data}))]
      resp)))

;; this needs to be configurable duh
(defn close-consumers []
  (println "closing consumers")
  (post "http://localhost:3000/api/events/close" {}))

(def ws-url "ws://localhost:3000/api/ws?topic=userspace.jobs-output")

(defrecord WebSocketSource [state url msg-ch listener-loop]
  component/Lifecycle
  (start [cmp]
    (if (:stream cmp)
      (do
        (println "already started stream")
        cmp)
      (do
        (println "starting websocket stream")
        (assoc cmp :stream
               (go-loop [reconnect-delay 1000]
                 (let [{:keys [ws-channel error]} (a/<! (ws-ch url))]
                   (if-let [websocket ws-channel]
                     (loop []
                       (println "websocket connected")
                       (let [{:keys [message error] :as msg} (a/<! websocket)]
                         (if message (js/console.log (str "message: " message)))
                         (if error (js/console.log (str "error: " error)))
                         (if message
                           (a/>! (:msg-ch cmp) message))
                         (if msg ;; closed?
                           (recur)
                           :error)))
                     (do
                       (println error)
                       (a/<! (a/timeout reconnect-delay))
                       (recur (* reconnect-delay 1.5)))))))))))

(defrecord EventSource [state url msg-ch listener-loop]
  component/Lifecycle
  (start [cmp]
    (if (:stream cmp) ;; (= 1 (:state cmp))  ;;not sure about this :state thing
      (do
        (println "already started stream")
        cmp)
      (do
        (println "starting event stream")
        (let [src (js/EventSource. (:url cmp) #js{ :withCredentials true } )]
          (set! (.-onmessage src) (fn [ev]
                                    (js/console.log "onmessage")
                                    (let [msg (cljs.reader/read-string ev.data)]
                                      (if (= msg :reconnect)
                                        (do
                                          (js/console.log "reconnecting")
                                          (component/stop cmp)
                                          (component/start cmp))
                                        ;; TODO transit?
                                        (a/put! (:msg-ch cmp) msg)))
                                    ))
          (set! (.-onerror src) (fn [ev]
                                  (js/console.log "onerror")
                                  (js/console.log ev)
                                  (.close src)
                                  ))
          (set! (.-onopen src) (fn [ev]
                                 (js/console.log "EventSource listening")))
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


;; close consumers so messages are not lost
;; (goog.events.listen js/window
;;                     goog.events.EventType.BEFOREUNLOAD
;;                     close-consumers)


(defn event-source [url msg-ch]
  ;; (map->EventSource {:url url :msg-ch msg-ch})
  (map->WebSocketSource {:url url :msg-ch msg-ch})
  )



(comment
  (def msg-ch (a/chan))

  (def es (event-source
           "http://localhost:3000/api/events?topic=userspace.jobs-output"
           msg-ch))
  (component/start es)
  (:stream es)
  (component/stop es)

  (a/take! msg-ch println true)

  (close-consumers)


  (go
    (let [msg-ch (ws-channel ws-url)]
      (loop []
        (let [{:keys [message error]} (a/<! msg-ch)]
          (if message
            (js/console.log "WS: Got message from server:" (pr-str message))
            (js/console.log "WS: Got error from server:" (pr-str error)))))))


  )

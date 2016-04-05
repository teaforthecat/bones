(ns bones.client.http
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [schema.core :as s])
  (:require [cljs-http.client :as http]
            [cljs.core.async :as a]))

(defn post [url data]
  (go
    (let [resp (<! (http/post url  {:edn-params data}))]
      resp)))

(defn channels []
  (atom {"onopen" (a/chan)
         "onerror" (a/chan (a/dropping-buffer 10))
         "onmessage" (a/chan 100) ;;notsureifwanttoblockconnection
         "onclose" (a/chan)
         "send" (a/chan 10)
         "close" (a/chan)}))

(defonce conn (channels))

(defn listen [url {:keys [:constructor] :or {constructor js/WebSocket. }}]
  "binds a js/websocket to a core-async channel event bus"
  (let [websocket (constructor url)
        webbus (a/chan)
        event-bus (a/pub webbus first)
        publish! #(a/put! webbus [%1 %2])
        subscribe! #(a/sub event-bus %1 (get @conn %1))]
    (doto websocket
      (aset "binaryType" "arraybuffer") ;;notsureifrequired
      (aset "onopen"    #(publish! "onopen" "open!"))
      (aset "onerror"   #(publish! "onerror" %))
      (aset "onmessage" #(publish! "onmessage" %))
      (aset "onclose"   #(do (publish! "onclose" "closed!")
                             (swap! conn dissoc "event-bus")
                             (a/close! webbus))))
    (a/sub event-bus "send" (get @conn "send"))
    (a/sub event-bus "close" (get @conn "close"))
    (go-loop []
        (let [msg (a/<! (get @conn "send"))]
          #(.send websocket %)
          (recur)))
    (go-loop []
        (let [msg (a/<! (get @conn "close"))]
          #(.close websocket %)))

    (subscribe! "onopen")
    (subscribe! "onerror")
    (subscribe! "onmessage")
    (subscribe! "onclose")
    (swap! conn assoc "event-bus" event-bus)
    websocket))

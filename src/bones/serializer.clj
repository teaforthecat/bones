(ns bones.serializer
  (:require [cognitect.transit :as transit])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def data-format :msgpack)


(defn encoder [{:keys [buffer data-format]}]
  (let [buff (or buffer (ByteArrayOutputStream. 4096))
        frmt (or data-format :msgpack)]
    (fn [data]
      (transit/write (transit/writer buff frmt) data)
      (.toByteArray buff))))

(defn decoder [{:keys [buffer data-format]}]
  (let [buff (or buffer (ByteArrayOutputStream. 4096))
        frmt (or data-format :msgpack)]
    (fn [data]
      (transit/read (transit/reader buff frmt)))))


(def serialize   (encoder {:data-format data-format }))
(def deserialize (decoder {:data-format data-format }))

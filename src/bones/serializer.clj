(ns bones.serializer
  (:require [cognitect.transit :as transit]
            ;[bones.core :as sys]
            )
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(defn data-format []
  :msgpack
     ;; ]->/bones/core->/bones/serializer->/bones/kafka->/bones/http->[
  #_(get-in sys/system [:conf :serializer :format])
  )

;; transit serialization
(defn transit-encoder [data-format]
  (fn [data]
    (.toByteArray
     (let [buf (ByteArrayOutputStream. 4096)
           writer (transit/writer buf data-format)]
       (transit/write writer data)
       buf))))

(defn transit-decoder [data-format]
  (fn [buffer]
    (transit/read (transit/reader (ByteArrayInputStream. buffer) data-format))))

(comment
  ((transit-decoder :json)
   ((transit-encoder :json) "hello"))
)


(def serialize   (transit-encoder (data-format)))
(def deserialize (transit-decoder (data-format)))

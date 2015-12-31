(ns userspace.jobs)

(defn wat [segment]
  (let [kafka-key (:_kafka-key segment)]
    (-> segment
        (dissoc :_kafka-key)
        (assoc :key kafka-key)
        (assoc-in [:message :processed] true)
        (assoc-in [:message :input] segment)
        (assoc-in [:message :output] {:a "a hammer"}))))

(defn who [segment]
  (let [kafka-key (:_kafka-key segment)]
    (-> segment
        (dissoc :_kafka-key)
        (assoc :key kafka-key)
        (assoc-in [:message :processed] true)
        (assoc-in [:message :input] segment)
        (assoc-in [:message :output] {:b "Mr. Charles"}))))

(defn where [segment]
  (let [kafka-key (:_kafka-key segment)]
    (-> segment
        (dissoc :_kafka-key)
        (assoc :key kafka-key)
        (assoc-in [:message :processed] true)
        (assoc-in [:message :input] segment)
        (assoc-in [:message :output] {:c "The Kitchen"}))))

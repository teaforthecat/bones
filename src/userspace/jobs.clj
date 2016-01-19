(ns userspace.jobs
  (:require [taoensso.timbre :as log]
            [bones.jobs :refer [defjob]]))

(defjob wat [message]
  (log/info "wat message: " message)
  {:a "a hammer"})

(defjob who [message]
  (log/info "who message: " message)
  {:b "Mr. Charles"})

(defjob where [message]
  (log/info "where message: " message)
  {:c "The Kitchen"})


(comment


  (wat {:message {:weight-kg 31, :name "aoeu"}
        :uuid #uuid "331d2a6e-8eae-4d1f-a9a1-0511ba26aefa",
        :_kafka-key 2})
  ;;=> {:message {:uuid #uuid "331d2a6e-8eae-4d1f-a9a1-0511ba26aefa", :output {:a "a hammer"}}, :key 2}

)

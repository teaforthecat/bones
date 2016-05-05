(ns userspace.jobs
  (:require [taoensso.timbre :as log]
            [bones.conf :as conf]
            [bones.db.riak :as riak]
            [bones.jobs :refer [defjob]]))


(defjob wat [message]
  (log/info "wat message: " message)
  (let [r (:riak @(resolve 'userspace.core/sys)) ;;help!
        bucket "wat"
        key (:uuid message)]
    (.put r bucket key (select-keys message [:name :weight-kg]))
    )
  {:a "a hammer"})

(defjob who [message]
  (log/info "who message: " message)
  (let [r (:riak @(resolve 'userspace.core/sys)) ;;help!
        bucket "who"
        key (:uuid message)]
    (.put r bucket key (select-keys message [:name :role]))
    )
  {:b "Mr. Charles" :_background {:fn :userspace.jobs/extra-work :args {:animal "this-and-that"}}})

(defjob where [message]
  (log/info "where message: " message)
  {:c "The Kitchen"})

(defn extra-work [thing]
  (log/info "extra-work args: " thing)
  (assoc thing :this "is in the background"))

(comment


  (wat {:message {:weight-kg 31, :name "aoeu"}
        :uuid #uuid "331d2a6e-8eae-4d1f-a9a1-0511ba26aefa",
        :_kafka-key 2})
  ;;=> {:message {:uuid #uuid "331d2a6e-8eae-4d1f-a9a1-0511ba26aefa", :output {:a "a hammer"}}, :key 2}

  )

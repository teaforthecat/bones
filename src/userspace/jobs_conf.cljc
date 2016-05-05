(ns userspace.jobs-conf
  (:require [schema.core :as s]))


(def some-jobs
  {:userspace.jobs/who {:name s/Str
                        :role s/Str}
   :userspace.jobs/wat {:weight-kg s/Num
                        :name s/Str}
   :userspace.jobs/where {:name s/Str
                          :room-number s/Int}}
  )


(def some-background-jobs
  {:userspace.jobs/extra-work {:animal s/Str}})

(def some-riak-search-indexes
  {"who" {:name s/Str
          :role s/Str}
   "wat" {:weight-kg s/Num
          :name s/Str}
   "where" {:name s/Str
            :room-number s/Int}}
  )

;; todo: maybe combine indexes and buckets somehow
(def some-riak-buckets
  {"who" {:index "who"}
   "wat" {:index "wat"}
   "where" {:index "where"}}
  )

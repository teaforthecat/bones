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

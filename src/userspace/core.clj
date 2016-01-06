(ns userspace.core
  (:require [bones.system :as system]
            [bones.http :as http]
            [userspace.jobs] ;;must be in classpath
            [onyx.plugin.kafka] ;;must be in classpath
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [compojure.core :as cj]
            ))


(def some-jobs
  {:userspace.jobs/wat {:weight-kg s/Num
                        :name s/Str}
   :userspace.jobs/who {:name s/Str
                        :role s/Str}
   :userspace.jobs/where {:name s/Str
                          :room-number s/Int}}
  )


(def handler (http/build-handler {:bones.http/path "/api"
                                  :bones/jobs some-jobs}))

(def sys (system/system {:conf-files ["resources/conf/test.edn"]
                         :http/handler #'userspace.core/handler
                         :bones.http/path "/api"
                         :bones/jobs some-jobs}))
(comment ;; various ways to start parts or all of the system
(system/start-system sys :http :conf)
(system/stop-system sys :http :conf)

(system/start-system sys :kafka :zookeeper :conf)
(system/stop-system sys :kafka :zookeeper :conf)

(system/start-system sys :http :kafka :zookeeper :conf)
(system/stop-system sys :http :kafka :zookeeper :conf)

(system/start-system sys :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)
(system/stop-system sys :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)

(system/start-system sys :onyx-peers :onyx-peer-group :conf)
(system/stop-system sys :onyx-peers :onyx-peer-group :conf)

(system/start-system sys :jobs :conf)
(system/stop-system sys :jobs)


(system/start-system sys)
(system/stop-system sys)
)

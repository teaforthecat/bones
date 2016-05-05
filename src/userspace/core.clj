(ns userspace.core
  (:require [bones.system :as system]
            [bones.http :as http]
            [userspace.system :refer [sys]]
            [taoensso.timbre :as log]
            [userspace.jobs] ;;must be in classpath
            [userspace.jobs-conf :refer [some-jobs
                                         some-background-jobs
                                         some-riak-search-indexes
                                         some-riak-buckets]]
            [onyx.plugin.kafka] ;;must be in classpath
            [schema.core :as s]
            [com.stuartsierra.component :as component]
            [compojure.core :as cj]
            ))

(def users
  [  {:username "admin"
      :password "secret"
      :roles [:admin]}
   {:username "jerry"
    :password "jerry"}])

(defn seed []
  (map bones.http/create-user users))

(defn query-handler [query-params]
  ;; (print query-params)
  (log/info "query-handler" query-params)
  (let [{:keys [q index start limit]} query-params]
    (bones.db.riak/riak-search (:riak @sys) index q {})))

(def handler (http/build-handler {:bones.http/path "/api"
                                  :bones/jobs some-jobs
                                  :bones.http/query-handler #'query-handler
                                  }))


(reset! sys (system/system {:conf-files ["resources/conf/test.edn"]
                            :http/handler #'userspace.core/handler
                            :bones.http/path "/api"
                            :bones/jobs some-jobs
                            :bones/background-jobs some-background-jobs}))

(swap! sys assoc :riak (component/using
                                       (bones.db.riak/map->Riak {:indexes userspace.jobs-conf/some-riak-search-indexes
                                                                 :buckets userspace.jobs-conf/some-riak-buckets})
                                       [:conf]))
(system/start-system sys :riak :conf)


(comment ;; various ways to start parts or all of the system

(seed)


(swap! sys assoc :riak (component/using
                        (bones.db.riak/map->Riak {:indexes some-riak-search-indexes
                                                  :buckets some-riak-buckets})
                        [:conf]))
(:riak @sys)
(system/start-system sys :riak :conf)

(system/start-system sys :http :conf)
(system/stop-system sys :http :conf)

(system/start-system sys :kafka :zookeeper :conf)
(system/stop-system sys :kafka :zookeeper :conf)

(system/start-system sys :http :kafka :zookeeper :conf)
(system/stop-system sys :http :kafka :zookeeper :conf)

(system/start-system sys :riak :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)
(system/stop-system sys  :riak :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)

(system/start-system sys :onyx-peers :onyx-peer-group :conf)
(system/stop-system sys :onyx-peers :onyx-peer-group :conf)

(system/start-system sys :riak :conf)

(system/start-system sys :jobs :conf)
(system/stop-system sys :jobs)


(system/start-system sys)
(system/stop-system sys)









;; hmm, on initialization? - sure
(.build-indexes (:riak @sys))
(.build-buckets (:riak @sys))
(:conn (:riak @sys))
(:buckets (:riak @sys))
(:indexes (:riak @sys))

(.put (:riak @sys) "wat" "abcd1234" {:x 1})
(.put (:riak @sys) "wat" "abcd12345" {:weight-kg 1
                                     :name "steven"})

(bones.db.riak/riak-search (:riak @sys) "wat" "*:*" {})

(.delete (:riak @sys) "wat" "abcd1234")


)

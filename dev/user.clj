(ns user
  (:import [com.google.protobuf ByteString])
  (:require [figwheel-sidecar.repl-api :as ra]
            [bones.system :as system]
            [bones.db.riak :refer [->Riak]]
            [com.stuartsierra.component :as component]
            [userspace.system :refer [sys]]
            [userspace.jobs-conf :refer [some-jobs
                                         some-background-jobs
                                         some-riak-search-indexes
                                         some-riak-buckets]]
            [userspace.core :as core]))

(defn bootup []
  (reset! sys (system/system {:conf-files core/conf-files
                              :http/handler #'core/handler
                              :bones.http/path "/api"
                              :bones/jobs some-jobs
                              :bones/background-jobs some-background-jobs}))
  (system/start-system sys :jobs :http :onyx-peers :onyx-peer-group :zookeeper :kafka :conf)
  ;; this is part of the user-chosen database integration
  (swap! sys assoc :riak (component/using
                          ;; fixme: :x is a placeholder for the connection
                          (->Riak :x some-riak-search-indexes some-riak-buckets)
                          [:conf]))
  (system/start-system sys :riak :conf)
  (userspace.core/seed))

(defn bootdown []
  (system/stop-system sys))


(defn start []
  (system/start-system sys :http :conf)
  (ra/start-figwheel!))

(defn stop []
  (system/stop-system sys :http :conf)
  (ra/stop-figwheel!))

(defn cljs [] (ra/cljs-repl "dev"))



(comment

  (defn cb-fn
    [p]
    (fn [asc e a] (deliver p [asc e a])))

  (defn connect
    []
    (let [p (promise)
          conn (client/connect "127.0.0.1" 8087 (cb-fn p))]
      @p
      conn))

  (def conn (connect))

  (defn schema-field-template
    "outputs a nice looking xml element"
    [field]
    {:pre [(string? (field 0)) ;name
           (string? (field 1))
           (instance? Boolean (field 2))]}
    (let [[name solr-type multi-valued] field]
      (format
       "    <field name=\"%s\" type=\"%s\" multiValued=\"%s\" indexed=\"true\" stored=\"true\"/>"
       name
       solr-type
       multi-valued)))

  (defn build-schema
    [name fields]
    (format (slurp "resources/solr-schema-template.xml")
            name
            (clojure.string/join "\n" (map schema-field-template fields))))

  (defn setup-schema
    [conn schema-name fields]
    (let [p (promise)
          content (build-schema schema-name fields)]
      (schema/put conn schema-name content (cb-fn p))
      (let [[asc e a] @p]
        a)))

  (setup-schema conn "who" [["name" "string" false]
                            ["role" "string" false]])
  (setup-schema conn "wat" [["name" "string" false]
                            ["weight-kg" "float" false]])
  (setup-schema conn "where" [["name" "string" false]
                              ["room-number" "int" false]])

  (defn get-schema
    [conn schema-name]
    (let [p (promise)]
      (schema/get conn schema-name (cb-fn p))
      (let [[asc e a] @p]
        a)))

  (println (get-schema conn "who"))
  (println (get-schema conn "wat"))
  (println (get-schema conn "where"))

  (defn setup-index
    [conn idx schema-name]
    (let [p (promise)]
      (index/put conn idx {:index {:schema schema-name}} (cb-fn p))
      (let [[asc e a] @p]
        a)))

  (setup-index conn "bones" "_yz_default")
  (setup-index conn "who" "who")
  (setup-index conn "wat" "wat")
  (setup-index conn "where" "where")

  (defn get-index
    [conn idx]
    (let [p (promise)]
      (index/get conn idx (cb-fn p))
      (let [[asc e a] @p]
        a)))

  (get-index conn "bones") ;; string not bytes I guess :)
  (get-index conn "who") ;; string not bytes I guess :)

  (defn setup-bucket
    [conn b idx]
    (let [p (promise)
          opts {:props {:search true
                        :search-index idx}}]
      (bucket/set conn b opts (cb-fn p))
      @p))

  (def who-bucket (byte-string<-utf8-string "who"))
  (def wat-bucket (byte-string<-utf8-string "wat"))
  (def where-bucket (byte-string<-utf8-string "where"))

  (setup-bucket conn who-bucket "who")
  (setup-bucket conn wat-bucket "wat")
  (setup-bucket conn where-bucket "where")

  (defn get-bucket
    [conn b]
    (let [p (promise)]
      (bucket/get conn b (cb-fn p))
      (let [[asc e a] @p]
        a)))

  ;; todo set :datatype
  (get-bucket conn who-bucket)
  (get-bucket conn wat-bucket)
  (get-bucket conn where-bucket)

  (defn json-bytes
    [value]
    (byte-string<-utf8-string (json/write-str value)))

  (defn put-object
    [conn b k json-byte-value]
    {:pre [(byte-string? b) (byte-string? k) (byte-string? json-byte-value)]}
    (let [v {:value json-byte-value
             :content-type "application/json"}
          p (promise)]
      (o/put conn b k v {} (cb-fn p))
      @p
      k))

  (put-object conn
              who-bucket
              (byte-string<-utf8-string "asoeuhrcho")
              (json-bytes {"name" "Nike"
                           "role" "God"}))
  (put-object conn
              who-bucket
              (byte-string<-utf8-string "uuid-1")
              (json-bytes {"name" "Styx"
                           "role" "God"}))
  (put-object conn
              who-bucket
              (byte-string<-utf8-string "uuid-2")
              (json-bytes {"name" "Pallas"
                           "role" "Titan"}))
  (put-object conn
              wat-bucket
              (byte-string<-utf8-string "wat-uuid-1")
              (json-bytes {"name" "hammer"
                           "weight-kg" "1.5"}))

  (put-object conn
              where-bucket
              (byte-string<-utf8-string "where-1")
              (json-bytes {"name" "Zothosthro"
                           "room-number" 1}))

  (defn get-object
    [conn b k opts]
    {:pre [(byte-string? b) (byte-string? k)]}
    (let [p (promise)]
      (o/get conn b k {} (cb-fn p))
      (let [[asc e a] @p]
        a)))

  (defn parse-object-result
    [obj]
    (utf8-string<-byte-string (get-in obj [:content 0 :value])))

  (get-object conn
              wat-bucket
              (byte-string<-utf8-string "wat-uuid-1")
              {})

  (defn parse-search-results
    "turn a list of vectors of maps with :key :value keys into a seq of maps"
    [docs]
    (map
     (fn [x]
       (reduce merge
               (map (fn [r] {(:key r) (:value r)})
                    x)))
     (map :fields docs)))

  (defn search
    [conn idx q opts]
    {:pre [(string? idx) (byte-string? q) (map? opts)]}
    (let [p (promise)]
      (s/search conn idx q opts (cb-fn p))
      (let [[asc e {:keys [docs num-found]}]  @p]
        {:docs (parse-search-results docs)
         :num-found num-found})))

  (search conn
            "who"
            (byte-string<-utf8-string "name:*")
            {})

  (search conn
            "wat"
            (byte-string<-utf8-string "name:*")
            {})

  (search conn
            "where"
            (byte-string<-utf8-string "name:*")
            {})

  (defn search-mapreduce-input
    [idx q]
    {:pre [(string? idx) (string? q)]}
    {:module "yokozuna"
     :function "mapred_search"
     :arg [idx q]})

  (defn search-mapreduce-query
    []
    [
     ;; {:map {;:module "riak_kv_mapreduce"
     ;;        :keep true
     ;;        :name "riak_object.get_value"
     ;;        :language "erlang"}}
     {:map {:language "javascript"
            :keep true
            :source "function(v,k,arg){ return v; } "}}
     ;; {:reduce {:language "javascript"
     ;;           :keep true
     ;;           }}
     ;; {:reduce {:arg nil
     ;;           :name "Riak.reduceIdentity"
     ;;           :language "javascript"
     ;;           :keep true}}
     ]
    )

  (defn map-reduce
    [conn job]
    {:pre [(map? job)]}
    (let [result (promise)
          stream (atom [])
          job-bytes (byte-string<-utf8-string (json/write-str job))
          stream-cb (fn [xs]
                     (if (and xs (not (zero? (.size ^ByteString xs))))
                       (swap! stream conj xs)
                       (deliver result @stream)))
          result-cb (fn [asc e a] (or a e))]
      (mr/map-reduce conn job-bytes result-cb stream-cb)
      @result))

  (map-reduce conn
   {:inputs (search-mapreduce-input "who" "name:*")
    :query (search-mapreduce-query )
    })


  ;; TODO make schema for each bucket based on some-jobs

   ;; <dynamicField name="*_flag"     type="boolean" indexed="true" stored="true" multiValued="false" />
   ;; <dynamicField name="*_counter"  type="int"     indexed="true" stored="true" multiValued="false" />
   ;; <dynamicField name="*_register" type="string"  indexed="true" stored="true" multiValued="false" />
   ;; <dynamicField name="*_set"      type="string"  indexed="true" stored="true" multiValued="true" />

  )

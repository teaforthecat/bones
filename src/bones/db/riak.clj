(ns bones.db.riak
  (:import [com.google.protobuf ByteString])
  (:require [com.stuartsierra.component :as component]
            [schema.core :as s]
            [schema.spec.core :as spec]
            [schema.utils :as utils]
            [clojure.data.json :as json]
            [kria.client :as client]
            [kria.index :as index]
            [kria.schema :as schema]
            [kria.bucket :as bucket]
            [kria.conversions :refer [byte-string?
                                      byte-string<-utf8-string
                                      utf8-string<-byte-string]]
            [kria.object :as o]
            [kria.search :as solr]
            [kria.map-reduce :as mr]))

;; fixme: handle errors better
;; redo event handling

(defn cb-fn
  [p]
  (fn [asc e a] (deliver p [asc e a])))

(defn connect
  [{:keys [:riak/host :riak/port]}]
  {:pre [(string? host) (integer? port)]}
  (let [p (promise)
        conn (client/connect host port (cb-fn p))]
    @p
    conn))

(defn json-bytes
  [value]
  (byte-string<-utf8-string (json/write-str value)))

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


(defn setup-index
  [conn idx schema-name]
  (let [p (promise)]
    (index/put conn idx {:index {:schema schema-name}} (cb-fn p))
    (let [[asc e a] @p]
      a)))

(defn setup-bucket
  [conn b idx]
  (let [p (promise)
        opts {:props {:search true
                      :search-index idx}}]
    (bucket/set conn (byte-string<-utf8-string b) opts (cb-fn p))
    (let [[asc e a] @p]
      a)))


(defn put-object
  [conn b k json-byte-value]
  {:pre [(byte-string? b) (byte-string? k) (byte-string? json-byte-value)]}
  (let [v {:value json-byte-value
           :content-type "application/json"}
        p (promise)]
    (o/put conn b k v {} (cb-fn p))
    @p
    k))

(defn fetch-object
  [conn b k]
  {:pre [(byte-string? b) (byte-string? k)]}
  (let [p (promise)]
    (o/get conn b k {} (cb-fn p))
    (let [[asc e a] @p]
      a)))

(defn delete-object
  [conn b k]
  {:pre [(byte-string? b) (byte-string? k)]}
  (let [p (promise)]
    (o/delete conn b k {} (cb-fn p))
    (let [[asc e a] @p]
      a)))

(defn parse-search-results
  "turn a list of vectors of maps with :key :value keys into a seq of maps"
  [docs]
  (map
   (fn [x]
     (reduce merge
             (map (fn [r] {(:key r) (:value r)})
                  x)))
   (map :fields docs)))

(defn -search
  [conn idx q opts]
  {:pre [(string? idx) (byte-string? q) (map? opts)]}
  (let [p (promise)]
    (if (.isOpen conn)
      (solr/search conn idx q opts (cb-fn p))
      (deliver p [nil {:error "not connected to Riak"} {}]))
    (let [[asc e {:keys [docs num-found]}]  @p]
      (if e (println e))                ;todo: handle errors
      {:docs (parse-search-results docs)
       :num-found num-found})))

(def type-map
  {s/Str "string"
   s/Num "float"
   s/Int "int"})

(defn build-index [entry]
  (let [[kname type] (first (vec entry)) ;; how to destructure a map to vec?
        multi false] ;; always single for now to keep the interface simple
    [(name kname) (type-map type) multi]))


(defn -build-indexes
  "ensure that schemas and indexes exist to configure buckets with
  builds a schema first, then an index with the same name"
  [conn indexes]
  (let [new-indexes (reduce-kv #(assoc %1 %2 (build-index %3)) {} indexes)]
    (map (partial apply setup-schema conn) new-indexes)
    (map (partial apply setup-index conn) (vec (zipmap (keys new-indexes) (keys new-indexes))))))

(defn -build-buckets [conn buckets]
  (let [new-bucket-defs (reduce-kv #(assoc %1 %2 (:index %3)) {} buckets)]
    (map (partial apply setup-bucket conn)
         new-bucket-defs)))

;; todo handle: java.nio.channels.ClosedChannelException
(defprotocol RiakConn
  "turns everything into json so it gets indexed, riak types are on their way"
  (put [this bucket key object])
  (fetch [this bucket key])
  (delete [this bucket key])
  (search [this idx query opts]))

(defprotocol RiakBuild
  (build-indexes [this])
  (build-buckets [this]))

(defrecord Riak [conf indexes buckets]
  component/Lifecycle
  (start [this]
    (if-let [conn (connect (select-keys conf [:riak/host :riak/port]))]
      (assoc this :conn conn)
      (throw (ex-info "Connection not established:" (select-keys conf [:riak/host :riak/port])) )))
  RiakConn
  (put [this bucket key object]
    (put-object (:conn this)
                (byte-string<-utf8-string bucket)
                (byte-string<-utf8-string key)
                (json-bytes object)))
  (fetch [this bucket key]
    (let [resp (fetch-object (:conn this)
                          (byte-string<-utf8-string bucket)
                          (byte-string<-utf8-string key))
          getin (comp :value first :content)]
      (if-let [bytes (getin resp)]
        (utf8-string<-byte-string bytes))))
  (delete [this bucket key]
    (delete-object (:conn this)
                   (byte-string<-utf8-string bucket)
                   (byte-string<-utf8-string key)))
  ;; todo hookup a stream
  (search [this idx query opts]
    ;; todo use (:indexes this)
    (-search (:conn this)
             idx
             (byte-string<-utf8-string query)
             opts))
  RiakBuild
  (build-indexes [this]
    ;; todo throw if nil :indexes
    ;; todo also setup search coercion functions
    (-build-indexes (:conn this) (:indexes this)))
  (build-buckets [this]
    (-build-buckets (:conn this) (:buckets this))))

(defn riak-search-coercion
  "coerce solr json into clojure maps
- keywordize keys
- coerce solr float to clojure num
 if the data doesn't match, schema.util.ErrorContainer is returned"
  [schema]
  (spec/run-checker
   (fn [s params]
     (let [walk (spec/checker (s/spec s) params)]
       (fn [x]
         (cond
           (and (map? s) (map? x))
               (try
                 (walk
                  (clojure.walk/keywordize-keys x))
               (catch java.lang.IllegalArgumentException e
                 ;; log or something
                 nil))
           (and (string? x)
                ;; look for solr Float
                (re-find #"e\+\d\d" x))
               (walk (read-string x)) ;; solr Float to clojure Num
           :else
           (walk x)))))
   true
   schema))

;; todo maybe catch java.io.IOException
(defn riak-search [riak-conn index query opts]
  (let [schema (get-in riak-conn [:indexes index])
        parser (riak-search-coercion (assoc schema s/Any s/Any))
        results (.search riak-conn index query opts)]
    {:results (->> (:docs results)
                    (mapv parser)
                    (remove #(= (type %) schema.utils.ErrorContainer))
                    (clojure.walk/postwalk-replace
                     {:_yz_rb :bucket
                      :_yz_rk :key
                      :_yz_id nil
                      :_yz_rt nil})
                    (map #(dissoc % nil)))
     :num-found (:num-found results)}))

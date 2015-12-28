(defproject bones "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/test.check "0.9.0"]
                 [org.danielsz/system "0.2.0"]
                 [aleph "0.4.1-beta3"]
                 [metosin/compojure-api "0.24.2"]
                 [org.onyxplatform/onyx-kafka "0.8.2.2"]
                 [com.cognitect/transit-clj "0.8.285"]
                 [ring-mock "0.1.5"]
                 [peridot "0.4.2"]
                 [prismatic/schema "1.0.3"]
                 [environ "1.0.1"]
                 ]
  :plugins [[lein-expectations "0.0.8"]]
  :profiles {:dev {:source-paths ["src"]
                   :dependencies [[expectations "1.4.45"]
                                  ;[expectations "2.0.9"]
                                  ]}
             :test {:dependencies [[expectations "2.0.9"]]}})

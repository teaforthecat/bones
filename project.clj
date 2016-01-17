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
                 [buddy/buddy-auth "0.8.1"]
                 [buddy/buddy-hashers "0.9.1"]
                 [datascript "0.13.1"]
                 [org.clojure/clojurescript "1.7.170"]
                 [re-frame "0.6.0"]
                 [reagent-forms "0.5.13"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.stuartsierra/component "0.3.1"] ;;updated
                 [prone "1.0.0"]
                 [bidi "1.25.0"]
                 [cljs-http "0.1.39"]
                 [ring-cors "0.1.7"]
                 ]

  ;; clojars was temporarily down
  ;; :repositories [["clojars" "https://clojars-mirror.tcrawley.org/repo/"]]
  ;; :plugins-repositories [["clojars" "https://clojars-mirror.tcrawley.org/repo/"]]
  ;; :mirrors      {"clojars" {:name "tcrawley"
  ;;                            :url "https://clojars-mirror.tcrawley.org/repo/" }}
  :cljsbuild {
              :builds [ { :id "dev"
                         :source-paths ["src/cljs" "src/"]
                         :figwheel true
                         :compiler {
                                    :main "userspace.core"
                                    :asset-path "js/out"
                                    :output-to "resources/public/js/app.js"
                                    :output-dir "resources/public/js/out" } } ]
              }


  :profiles  {:dev
              {:dependencies [[figwheel-sidecar "0.5.0-2"]
                              [com.cemerick/piggieback "0.2.1"] ]
               :source-paths ["src" "src/cljs" "dev"] }}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  ;; in ~/.lein/profiles.clj:
   ;; :repl {:plugins [[cider/cider-nrepl "0.10.0"]

  )

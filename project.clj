(defproject bones "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/test.check "0.9.0"]
                 [org.danielsz/system "0.2.0"]
                 [yada "1.0.0-20150903.093751-9"]
                 [aleph "0.4.1-beta3"]
                 [ring-mock "0.1.5"]]
  :profiles {:dev {:source-paths ["src"]}})

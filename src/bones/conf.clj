(ns bones.conf
  (:require [com.stuartsierra.component :as component]
            [clojure.edn :as edn]))

(defn quiet-slurp [file-path]
  (try (edn/read-string (slurp file-path))
       (catch java.io.FileNotFoundException e
         (println (str "WARNING: conf file not found: " file-path)))))

(defn read-conf-data [conf-files]
  (->> conf-files
       (map quiet-slurp)
       (reduce merge {})))

;; must return a Conf record, not just any map.
(defrecord Conf [conf-files sticky-keys mappy-keys]
  component/Lifecycle
  (start [cmp]
    (let [conf-data (read-conf-data conf-files)]
      (reduce
       (fn [c [dup k]] ;; maps one key onto another
         (assoc c dup (k c)))
       (map->Conf (merge cmp conf-data))
       mappy-keys)
      ))
  (stop [cmp]
    ;; keep the specified sticky keys and the special keys themselves
    (map->Conf (select-keys cmp (conj sticky-keys :sticky-keys :mappy-keys)))))

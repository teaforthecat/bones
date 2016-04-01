(ns bones.client
  (:require-macros [schema.core :as s])
  (:require [bones.client.db :as db]
            [cljs-uuid-utils.core :as uuid]
            [cljs-http.client :as http]))


(defn new-uuid []
  (uuid/make-random-uuid))

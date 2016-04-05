(ns bones.util-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer-macros [deftest is testing run-tests async]]
            [cljs.core.async :as a]
            ;; [bones.client.db :refer [->Transaction ->Reaction conn query]]
            ;; [petrol.core :as petrol]
            ;; [datascript.core :as d]
            [bones.client.http :as http ]
            [bones.client :as client]))

(defn test-async
  "Asynchronous test awaiting ch to produce a value or close."
  [ch]
  (async done
         (a/take! ch (fn [_] (done)))))

;; async test broken for now: WARNING: Async test called done more than one time.
;; (deftest listen-test
(defn listen-test
  (testing "connecting channels"
    (let [ch (a/chan)]
      (async done
             (let [websocket (http/listen "xyz" {:constructor (partial js-obj "send" #() "close" #() "url" )})]
               ((aget websocket "onopen"))
               (a/take! (get @http/conn "onopen")
                        #(do
                           (is (= "open!" %))
                           (done))))))))

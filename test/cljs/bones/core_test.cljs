(ns bones.core-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer-macros [deftest is testing run-tests async]]
            [cljs.core.async :as a]
            [bones.client.db :refer [->Transaction ->Reaction conn query]]
            [petrol.core :as petrol]
            [datascript.core :as d]
            [bones.client :as client]))


(deftest test-new-uuid
  (testing "not the same"
    (let [uuid (client/new-uuid)]
      (is (not (= uuid (client/new-uuid)))))))

(defrecord Button [button-id query]
  bones.client.db/React
  (q [button]
    (condp = query
      :count ['[:find ?count
                :in $ ?button-id
                :where [?button-id :counter ?count]]
              button-id]))
  bones.client.db/Transact
  (tx [button]
    {:db/id (:button-id button) :counter 1}))

(defn with-timeout [milli done]
  (go (a/<! (a/timeout milli)) (is (not "timeout occured")) (done)))

(deftest test-messages
  (testing "set a counter by sending a Click message with a button-id"
    (async done
           (let [mock-click (js/Event. "click")
                 button-id 42
                 render-fn (fn [ui-channel app-state]
                             (apply (petrol/send! ui-channel (->Transaction (->Button button-id nil))) [mock-click]))
                 reactor @(->Reaction (->Button button-id :count) nil nil)]
             (petrol/start-message-loop! conn render-fn)
             (with-timeout 3000 done)

             ;; somehow, having only one watch on this atom breaks the auto-running of doo
             ;; not sure wat is going on
             (add-watch reactor :wat (fn [_ _ _ new-value]
                                       (is (= 1 (ffirst new-value)))
                                       (done)))

             (add-watch reactor :done (fn [_ _ _ new-value]
                                        (is (= 1 (ffirst new-value)))
                                        (done)))))))

(ns bones.core-test
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.test :refer-macros [deftest is testing run-tests async]]
            [cljs.core.async :as a]
            [bones.client.db :refer [->Transaction conn query]]
            [petrol.core :as petrol]
            [datascript.core :as d]
            [bones.client :as client]))


(deftest test-new-uuid
  (testing "not the same"
    (let [uuid (client/new-uuid)]
      (is (not (= uuid (client/new-uuid)))))))

(defrecord Button [button-id]
  bones.client.db/Transact
  (tx [button]
    {:db/id (:button-id button) :counter 1}))

(deftest test-messages
  (testing "set a counter by sending a Click message with a button-id"
    (async done
           (let [mock-click (js/Event. "click")
                 button-id 42
                 render-fn (fn [ui-channel app-state]
                             (apply (petrol/send! ui-channel (->Transaction (->Button button-id))) [mock-click]))]
             (petrol/start-message-loop! conn render-fn)
             (go (a/<! (a/timeout 3000)) (is (not "timeout occured")) (done))
             (d/listen! @conn :test (fn [datoms]
                                      (let [count-result (query '[:find ?count
                                                                  :in $ ?button-id
                                                                  :where [?button-id :counter ?count]]
                                                                 button-id)]
                                        (is (= 1 (ffirst count-result)))
                                        (done))))))))

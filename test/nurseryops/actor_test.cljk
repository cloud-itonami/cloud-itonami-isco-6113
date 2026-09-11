(ns nurseryops.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [nurseryops.actor :as actor]
            [nurseryops.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-grower! st {:grower-id "grower-1" :name "Kobo Yamada"})
    (store/register-nursery! st {:nursery-id "N-1" :name "Kobo Nursery Site" :max-supply-cost 2000})
    st))

(deftest commits-a-registered-work-log
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:grower-id "grower-1" :op :log-work-record :stake :low
                  :nursery-id "N-1" :task "planting progress log"}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "grower-1"))))))

(deftest holds-an-unregistered-nursery-proposal
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:grower-id "grower-1" :op :log-work-record :stake :low
                  :nursery-id "N-ghost" :task "planting progress log"}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "grower-1")))))

(deftest interrupts-then-approves-safety-concern-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:grower-id "grower-1" :op :flag-safety-concern :stake :low
                  :nursery-id "N-1" :hazard-type :pesticide-exposure-risk}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "grower-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "grower-1")))))))

(deftest holds-a-scope-excluded-op-even-at-high-confidence
  (testing "an actor run can never commit a proposal that would finalize a cultivation-execution decision, regardless of disposition path"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          request {:grower-id "grower-1" :op :finalize-planting-decision :stake :low
                    :nursery-id "N-1" :task "planting decision"}
          result (actor/run-request! graph request {} "thread-4")]
      (is (= :done (:status result)))
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/records-of st "grower-1"))))))

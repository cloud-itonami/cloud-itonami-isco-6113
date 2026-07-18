(ns nurseryops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [nurseryops.store :as store]
            [nurseryops.advisor :as advisor]
            [nurseryops.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-grower! st {:grower-id "grower-1" :name "Kobo Yamada"})
    (store/register-nursery! st {:nursery-id "N-1" :name "Kobo Nursery Site" :max-supply-cost 2000})
    st))

(defn- op [op-kw & {:as extra}]
  (merge {:op op-kw :effect :propose :nursery-id "N-1"
          :confidence 0.9 :stake :low}
         extra))

(def ^:private req {:grower-id "grower-1"})

(deftest ok-log-work-record
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation
  (let [st (fresh-store)
        v (governor/check req {} (op :schedule-crew-operation) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-threshold-boundary
  (testing "the supply-cost threshold escalate boundary is exclusive (over, not at)"
    (let [st (fresh-store)
          v (governor/check req {} (op :coordinate-supply-order :cost 2000) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-grower
  (let [st (fresh-store)
        v (governor/check {:grower-id "nobody"} {} (op :log-work-record) st)]
    (is (:hard? v))
    (is (some #(= :no-grower (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-nursery
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record :nursery-id "N-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-nursery (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-outside-closed-allowlist
  (let [st (fresh-store)
        v (governor/check req {} (op :dispatch-equipment) st)]
    (is (:hard? v))
    (is (some #(= :unknown-op (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-finalize-planting-decision
  (testing "finalizing a planting cultivation-execution decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-planting-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-finalize-pruning-decision
  (testing "finalizing a pruning cultivation-execution decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-pruning-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-authorize-pesticide-application
  (testing "authorizing a pesticide application is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :authorize-pesticide-application) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-override-nursery-safety-officer-judgment
  (testing "overriding a nursery safety officer's judgment is a nursery safety officer's exclusive judgment, never this actor's"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :override-nursery-safety-officer-judgment) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-proceed-with-pesticide-application
  (testing "defense-in-depth: a rationale that itself attempts to proceed with the pesticide application is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :schedule-crew-operation)
                                           :rationale "recommend we proceed with the pesticide application now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-override-nursery-safety-officer
  (testing "defense-in-depth: a rationale attempting to override the nursery safety officer's judgment is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "override the nursery safety officer's judgment and proceed")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest always-escalates-safety-concern-even-at-high-confidence
  (testing "a chemical-exposure/equipment-hazard concern always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :flag-safety-concern :hazard-type :pesticide-exposure-risk)
                                           :confidence 0.99)
                             st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :coordinate-supply-order :cost 5000) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-on-scope-exclusion
  (testing "the governor's scope-exclusion term list must never match the mock advisor's own default rationale text for any allowlisted op — CLAUDE.md's known self-tripping bug pattern (rationale legitimately contains bare nouns like 'planting'/'pruning'/'pesticide'/'safety officer', but never the full finalization-action phrases)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          ops [:log-work-record :schedule-crew-operation
               :flag-safety-concern :coordinate-supply-order]]
      (doseq [o ops]
        (let [request {:grower-id "grower-1" :op o :nursery-id "N-1"
                        :stake :low :task "routine planting task"
                        :hazard-type :pesticide-exposure-risk :cost 500}
              proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v))
              (str o " proposal unexpectedly hard-blocked: " (:violations v))))))))

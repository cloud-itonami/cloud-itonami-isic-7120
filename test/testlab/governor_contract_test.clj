(ns testlab.governor-contract-test
  "The governor contract as executable tests -- the technical-testing/
  analysis analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    TestLab-LLM never certifies an engagement the Test Integrity
    Governor would reject, `:certification/issue` NEVER auto-commits
    at any phase, `:engagement/intake` (no capital risk) MAY auto-
    commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [testlab.store :as store]
            [testlab.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :laboratory-director :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :engagement/intake :subject "engagement-1"
                   :patch {:id "engagement-1" :client "Sakura Materials K.K."}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Materials K.K." (:client (store/engagement db "engagement-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "engagement-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "engagement-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "engagement-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "engagement-1")) "no assessment written"))))

(deftest certification-issue-without-assessment-is-held
  (testing "certification/issue before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :certification/issue :subject "engagement-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest out-of-tolerance-below-is-held
  (testing "a measured value below its own protocol's tolerance range -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "engagement-3")
          res (exec-op actor "t5" {:op :certification/issue :subject "engagement-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/certification-history db))))))

(deftest out-of-tolerance-above-is-held
  (testing "a measured value above its own protocol's tolerance range -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t6pre" "engagement-4")
          res (exec-op actor "t6" {:op :certification/issue :subject "engagement-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:out-of-tolerance} (-> (store/ledger db) last :basis)))
      (is (empty? (store/certification-history db))))))

(deftest calibration-not-current-is-held-and-unoverridable
  (testing "a stale instrument calibration on an engagement -> HOLD, and never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :calibration/screen :subject "engagement-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:calibration-not-current} (-> (store/ledger db) first :basis)))
      (is (nil? (store/calibration-of db "engagement-5")) "no clearance written"))))

(deftest certification-issue-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, in-tolerance engagement still ALWAYS interrupts for human approval -- actuation/issue-certification is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "engagement-1")
          r1 (exec-op actor "t8" {:op :certification/issue :subject "engagement-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, certification record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:certified? (store/engagement db "engagement-1"))))
          (is (= 1 (count (store/certification-history db))) "one draft certification record")))))
  (testing "reject -> hold, nothing certified"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "engagement-1")
          _ (exec-op actor "t9" {:op :certification/issue :subject "engagement-1"} operator)
          r2 (g/run* actor {:approval {:status :rejected :by "op-1"}}
                     {:thread-id "t9" :resume? true})]
      (is (= :hold (get-in r2 [:state :disposition])))
      (is (empty? (store/certification-history db)) "nothing certified on reject"))))

(deftest certification-issue-double-certification-is-held
  (testing "certifying the same engagement twice -> HOLD on the second attempt, even though the figures match cleanly"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "engagement-1")
          _ (exec-op actor "t10a" {:op :certification/issue :subject "engagement-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :certification/issue :subject "engagement-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-certified} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/certification-history db))) "still only the one earlier certification"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :engagement/intake :subject "engagement-1"
                          :patch {:id "engagement-1" :client "Sakura Materials K.K."}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "engagement-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))

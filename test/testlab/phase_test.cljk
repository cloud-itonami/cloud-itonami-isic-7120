(ns testlab.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:certification/issue` must NEVER be a member of any
  phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [testlab.phase :as phase]))

(deftest certification-issue-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real certification issuance"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :certification/issue))
          (str "phase " n " must not auto-commit :certification/issue")))))

(deftest calibration-screen-never-auto-at-any-phase
  (testing "screening moves no capital, but is still never auto-eligible, matching every sibling KYC/conflict/independence/surveillance screen"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :calibration/screen))
          (str "phase " n " must not auto-commit :calibration/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":engagement/intake moves no capital -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:engagement/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :engagement/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :certification/issue} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :engagement/intake} :commit)))))

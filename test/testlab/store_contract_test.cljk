(ns testlab.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [testlab.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Materials K.K." (:client (store/engagement s "engagement-1"))))
      (is (= "JPN" (:jurisdiction (store/engagement s "engagement-1"))))
      (is (= 500 (:measured-value (store/engagement s "engagement-1"))))
      (is (true? (:calibration-current? (store/engagement s "engagement-1"))))
      (is (false? (:calibration-current? (store/engagement s "engagement-5"))))
      (is (false? (:certified? (store/engagement s "engagement-1"))))
      (is (= ["engagement-1" "engagement-2" "engagement-3" "engagement-4" "engagement-5"]
             (mapv :id (store/all-engagements s))))
      (is (nil? (store/calibration-of s "engagement-1")))
      (is (nil? (store/assessment-of s "engagement-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/certification-history s)))
      (is (zero? (store/next-sequence s "JPN")))
      (is (false? (store/engagement-already-certified? s "engagement-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :engagement/upsert
                                 :value {:id "engagement-1" :client "Sakura Materials K.K."}})
        (is (= "Sakura Materials K.K." (:client (store/engagement s "engagement-1"))))
        (is (= 500 (:measured-value (store/engagement s "engagement-1"))) "measured-value preserved"))
      (testing "assessment / calibration payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["engagement-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "engagement-1")))
        (store/commit-record! s {:effect :calibration/set :path ["engagement-1"]
                                 :payload {:engagement-id "engagement-1" :verdict :current}})
        (is (= {:engagement-id "engagement-1" :verdict :current} (store/calibration-of s "engagement-1"))))
      (testing "certification issuance drafts a certification record and advances the sequence"
        (store/commit-record! s {:effect :engagement/mark-certified :path ["engagement-1"]})
        (is (= "JPN-CERT-000000" (get (first (store/certification-history s)) "record_id")))
        (is (= "certification-draft" (get (first (store/certification-history s)) "kind")))
        (is (true? (:certified? (store/engagement s "engagement-1"))))
        (is (= 1 (count (store/certification-history s))))
        (is (= 1 (store/next-sequence s "JPN")))
        (is (true? (store/engagement-already-certified? s "engagement-1")))
        (is (false? (store/engagement-already-certified? s "engagement-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/engagement s "nope")))
    (is (= [] (store/all-engagements s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/certification-history s)))
    (is (zero? (store/next-sequence s "JPN")))
    (store/with-engagements s {"x" {:id "x" :client "c" :sample-description "d" :test-protocol :tensile-strength
                                    :measured-value 500 :protocol-min 400 :protocol-max 600
                                    :calibration-current? true :certified? false
                                    :jurisdiction "JPN" :status :intake}})
    (is (= "c" (:client (store/engagement s "x"))))))

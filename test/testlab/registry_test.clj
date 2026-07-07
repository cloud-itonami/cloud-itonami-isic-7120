(ns testlab.registry-test
  (:require [clojure.test :refer [deftest is]]
            [testlab.registry :as r]))

;; ----------------------------- within-tolerance? -----------------------------

(deftest within-tolerance-when-value-inside-inclusive-range
  (is (r/within-tolerance? {:measured-value 400 :protocol-min 400 :protocol-max 600}))
  (is (r/within-tolerance? {:measured-value 600 :protocol-min 400 :protocol-max 600}))
  (is (r/within-tolerance? {:measured-value 500 :protocol-min 400 :protocol-max 600})))

(deftest out-of-tolerance-when-value-outside-either-bound
  (is (not (r/within-tolerance? {:measured-value 399 :protocol-min 400 :protocol-max 600})))
  (is (not (r/within-tolerance? {:measured-value 601 :protocol-min 400 :protocol-max 600}))))

;; ----------------------------- register-certification -----------------------------

(deftest certification-is-a-draft-not-a-real-certification
  (let [result (r/register-certification "engagement-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest certification-assigns-certification-number
  (let [result (r/register-certification "engagement-1" "JPN" 7)]
    (is (= (get result "certification_number") "JPN-CERT-000007"))
    (is (= (get-in result ["record" "engagement_id"]) "engagement-1"))
    (is (= (get-in result ["record" "kind"]) "certification-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest certification-validation-rules
  (is (thrown? Exception (r/register-certification "" "JPN" 0)))
  (is (thrown? Exception (r/register-certification "engagement-1" "" 0)))
  (is (thrown? Exception (r/register-certification "engagement-1" "JPN" -1))))

(deftest certification-history-is-append-only
  (let [c1 (r/register-certification "engagement-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-certification "engagement-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CERT-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CERT-000001" (get-in hist2 [1 "record_id"])))))

(ns testlab.registry
  "Pure-function certification-issuance record construction -- an
  append-only testing-laboratory book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a certification reference number -- every
  lab/jurisdiction assigns its own reference format. This namespace
  does NOT invent one; it builds a jurisdiction-scoped sequence number
  and validates the record's required fields, the same honest, non-
  fabricating discipline `testlab.facts` uses.

  `within-tolerance?` is a REAL, foundational testing-lab concept (a
  measured value must fall within a test protocol's own acceptance
  range), not an invented placeholder -- see its own docstring for the
  honest simplification it makes vs. a real uncertainty-of-measurement
  analysis. Unlike `marketadmin.registry/listing-standard-met?`
  (MINIMUM only) or `casualty.governor`'s/`realty.governor`'s/
  `credit.governor`'s cap-checks (MAXIMUM only), this is the FIRST
  check in this fleet to combine BOTH directions in ONE check -- a
  measured value must satisfy `protocol-min <= value <= protocol-max`,
  synthesizing the two inequality directions this fleet established
  separately into a single tolerance-band comparison.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real lab-information/instrument system. It builds the
  RECORD a laboratory would keep, not the act of issuing the
  certification itself (that is `testlab.operation`'s `:certification/
  issue`, always human-gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  accredited laboratory's act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn within-tolerance?
  "Does `engagement`'s own `:measured-value` fall within its own
  `:protocol-min`/`:protocol-max` acceptance range (inclusive)? A pure
  ground-truth check against the engagement's own permanent fields --
  see ns docstring for the honest simplification this makes vs. a real
  uncertainty-of-measurement analysis (this R0 does not model
  measurement uncertainty, only whether the point value itself falls
  in-range)."
  [{:keys [measured-value protocol-min protocol-max]}]
  (and (>= (double measured-value) (double protocol-min))
       (<= (double measured-value) (double protocol-max))))

(defn register-certification
  "Validate + construct the CERTIFICATION registration DRAFT -- the
  laboratory's own legal act of issuing a real test result/
  certification. Pure function -- does not touch any real lab-
  information/reporting system; it builds the RECORD a laboratory
  would keep. `testlab.governor` independently re-verifies the
  engagement's own measured value against its own tolerance range, and
  blocks a double-issuance of the same engagement, before this is
  ever allowed to commit."
  [engagement-id jurisdiction sequence]
  (when-not (and engagement-id (not= engagement-id ""))
    (throw (ex-info "certification: engagement_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "certification: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "certification: sequence must be >= 0" {})))
  (let [cert-number (str (str/upper-case jurisdiction) "-CERT-" (zero-pad sequence 6))
        record {"record_id" cert-number
                "kind" "certification-draft"
                "engagement_id" engagement-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certification_number" cert-number
     "certificate" (unsigned-certificate "TestCertification" cert-number cert-number)}))

(defn append
  "Append a certification record, returning a NEW list (never mutate
  history in place)."
  [history result]
  (conj (vec history) (get result "record")))

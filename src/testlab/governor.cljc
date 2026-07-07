(ns testlab.governor
  "Test Integrity Governor -- the independent compliance layer that
  earns the TestLab-LLM the right to commit. The LLM has no notion of
  jurisdictional lab-accreditation law, whether an engagement's own
  measured value actually falls within its test protocol's tolerance
  range, whether the instrument used is actually calibration-current,
  or when an act stops being a draft and becomes a real-world test
  result/certification, so this MUST be a separate system able to
  *reject* a proposal and fall back to HOLD -- the technical-testing
  analog of `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Five checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete accreditation
  evidence, a measured value outside its own protocol's tolerance
  range, a not-current instrument calibration, or certifying the same
  engagement twice). The confidence/actuation gate is SOFT: it asks a
  human to look (low confidence / actuation), and the human may
  approve -- but see `testlab.phase`: for `:stake :actuation/issue-
  certification` (a real test result/certification) NO phase ever
  allows auto-commit either. Two independent layers agree that
  actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`testlab.
                                       facts`), or invent one? Like
                                       `credit.governor`'s/`marketadmin.
                                       governor`'s actuation ops,
                                       `:certification/issue` acts
                                       directly on a pre-seeded
                                       engagement (see `testlab.store`'s
                                       own docstring) -- there is no
                                       'engagement is missing' failure
                                       mode to guard against here.
    2. Evidence incomplete         -- for `:certification/issue`, has the
                                       jurisdiction actually been
                                       assessed with a full accreditation
                                       evidence checklist on file?
    3. Out of tolerance            -- for `:certification/issue`,
                                       INDEPENDENTLY recompute whether
                                       the engagement's own `:measured-
                                       value` falls within its own
                                       `:protocol-min`/`:protocol-max`
                                       range (`testlab.registry/within-
                                       tolerance?`) -- needs no proposal
                                       inspection or stored-verdict
                                       lookup at all, the SAME shape
                                       `credit.governor/affordability-
                                       exceeded-violations`/`accounting.
                                       governor/trial-balance-out-of-
                                       balance-violations`/`marketadmin.
                                       governor/listing-standard-not-met-
                                       violations` establish -- but the
                                       FIRST check in this fleet to
                                       combine a MINIMUM and a MAXIMUM
                                       bound in one comparison.
    4. Calibration not current     -- reported by THIS proposal itself
                                       (a `:calibration/screen` that just
                                       found a stale calibration), or
                                       already on file for the
                                       engagement (`:calibration/screen`/
                                       `:certification/issue`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME discipline
                                       `casualty.governor/sanctions-
                                       violations`/`marketadmin.governor/
                                       surveillance-flag-unresolved-
                                       violations` established -- a
                                       calibration gap blocks
                                       certification even if the
                                       screening op itself never
                                       (re)ran in this session.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:certification/
                                       issue` (a REAL testing-lab act)
                                       -> escalate.

  One more guard, double-certification prevention, is enforced but NOT
  listed as a numbered HARD check above because it needs no upstream
  comparison at all -- `already-certified-violations` refuses to
  certify the SAME engagement twice, off a dedicated `:certified?` fact
  (never a `:status` value) -- the SAME 'check a dedicated boolean, not
  status' discipline `accounting.governor`'s/`marketadmin.governor`'s
  guards establish, informed by `cloud-itonami-isic-6492`'s status-
  lifecycle bug (ADR-2607071320)."
  (:require [testlab.facts :as facts]
            [testlab.registry :as registry]
            [testlab.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Issuing a real test result/certification is the ONE real-world
  actuation event this actor performs -- a single-member set, matching
  `cloud-itonami-isic-6511`'s/`6621`'s/`6629`'s/`6612`'s/`6492`'s
  single-actuation shape."
  #{:actuation/issue-certification})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:certification/issue`) proposal with no
  spec-basis citation is a HARD violation -- never invent a
  jurisdiction's lab-accreditation/certification-standard
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :certification/issue} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:certification/issue`, the jurisdiction's required chain-of-
  custody/test-protocol/calibration-certificate/accreditation-
  certificate evidence must actually be satisfied -- do not trust the
  advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :certification/issue)
    (let [e (store/engagement st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction e) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(サンプル管理記録/校正証明書/試験所認定証明書等)が充足していない状態での証明書発行提案"}]))))

(defn- out-of-tolerance-violations
  "For `:certification/issue`, INDEPENDENTLY recompute whether the
  engagement's own measured value satisfies its own protocol tolerance
  range via `testlab.registry/within-tolerance?` -- needs no proposal
  inspection or stored-verdict lookup at all, since its inputs are
  permanent ground-truth fields already on the engagement. The FIRST
  check in this fleet to combine a MINIMUM and a MAXIMUM bound in one
  comparison."
  [{:keys [op subject]} st]
  (when (= op :certification/issue)
    (let [e (store/engagement st subject)]
      (when-not (registry/within-tolerance? e)
        [{:rule :out-of-tolerance
          :detail (str subject " の測定値(" (:measured-value e)
                      ")が許容範囲[" (:protocol-min e) "," (:protocol-max e) "]の外にある")}]))))

(defn- calibration-not-current-violations
  "A not-current instrument calibration -- reported by THIS proposal
  (e.g. a `:calibration/screen` that itself just found a stale
  calibration), or already on file in the store for the engagement
  (`:calibration/screen`/`:certification/issue`) -- is a HARD, un-
  overridable hold. Evaluated UNCONDITIONALLY (not scoped to a specific
  op) so the screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :not-current (get-in proposal [:value :verdict]))
        engagement-id (when (contains? #{:calibration/screen :certification/issue} op) subject)
        hit-on-file? (and engagement-id (= :not-current (:verdict (store/calibration-of st engagement-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :calibration-not-current
        :detail "校正が最新でない試験装置を使用した提案は進められない"}])))

(defn- already-certified-violations
  "For `:certification/issue`, refuses to certify the SAME engagement
  twice, off a dedicated `:certified?` fact (never a `:status` value)
  -- see ns docstring for why this sidesteps the status-lifecycle risk
  `cloud-itonami-isic-6492`'s ADR-0001 documents."
  [{:keys [op subject]} st]
  (when (= op :certification/issue)
    (when (store/engagement-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に証明書発行済み")}])))

(defn check
  "Censors a TestLab-LLM proposal against the governor rules. Returns
   {:ok? bool :violations [..] :confidence c :escalate? bool :high-stakes? bool
    :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (out-of-tolerance-violations request st)
                           (calibration-not-current-violations request proposal st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})

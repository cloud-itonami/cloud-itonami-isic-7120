# ADR-0001: cloud-itonami-isic-7120 -- TestLab-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611` ADR-0001s (the pattern this
  ADR ports); ADR-2607071250/ADR-2607071320/ADR-2607071351/
  ADR-2607071618 (`6612`/`6492`/`6920`/`6611`, the four verticals built
  outside ADR-2607032000's original insurance/real-estate batch --
  this is the fifth)
- Context: Continuing the standing "pick a new ISIC blueprint vertical"
  direction past `6611`, this ADR deepens `cloud-itonami-isic-7120`
  (technical testing and analysis) from `:blueprint` to `:implemented`,
  the thirteenth actor in this fleet -- the FIRST technical-testing/
  professional-scientific-services vertical (ISIC division 71), and,
  like `6920`, an actor whose blueprint names NO bespoke domain
  capability lib at all.

## Problem

A technical-testing/analysis laboratory's certification workflow
bundles several distinct concerns under one governed workflow:

1. **Jurisdiction lab-accreditation/certification-standard
   correctness** -- is the required evidence for issuing a test
   result/certification based on an official accreditation body
   (NITE/A2LA/UKAS/DAkkS), or invented?
2. **Measurement tolerance** -- does an engagement's own measured
   value actually fall within its own test protocol's own acceptance
   range? Unlike every prior threshold check in this fleet (`casualty.
   governor`'s/`realty.governor`'s/`credit.governor`'s MAXIMUM-only
   caps, `marketadmin.governor`'s MINIMUM-only floor), a laboratory
   tolerance range is inherently TWO-SIDED -- a measurement can fail by
   being either too low or too high.
3. **Instrument calibration currency** -- does the engagement's
   instrument carry a current calibration, or a stale one? The
   testing-lab-specific reuse of the unconditional-evaluation
   screening discipline this fleet's `casualty.governor/sanctions-
   violations` originally established.
4. **Real actuation, once** -- issuing a real test result/certification
   is an irreversible act a client, a regulator, or a downstream buyer
   of the tested goods will rely on.

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a testing laboratory with an LLM" but
"seal the LLM inside a trust boundary and layer evidence-sufficiency,
two-sided tolerance verification, calibration-currency screening,
audit and human-approval on top of it, while structurally fixing the
one real actuation event as human-only."

## Decision

### 1. TestLab-LLM is sealed into the bottom node; it never certifies directly

`testlab.testlabllm` returns exactly four kinds of proposal: intake
normalization, jurisdiction accreditation checklist, calibration
screening, and certification draft. No proposal writes the SSoT or
commits a real certification directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 technical-testing/analysis operation

`testlab.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `within-tolerance?` is the FIRST two-sided range check in this fleet

`testlab.registry/within-tolerance?` requires `protocol-min <=
measured-value <= protocol-max` -- combining, in ONE comparison, the
two inequality directions this fleet previously established
separately (`marketadmin.registry/listing-standard-met?`'s MINIMUM-
only floor; `casualty.governor`'s/`realty.governor`'s/`credit.
governor`'s MAXIMUM-only caps). `out-of-tolerance-violations` reuses
the SAME pure-ground-truth-recompute shape `credit.governor/
affordability-exceeded-violations`/`accounting.governor/trial-balance-
out-of-balance-violations`/`marketadmin.governor/listing-standard-not-
met-violations` establish -- no proposal inspection or stored-verdict
lookup needed at all, since its inputs (`:measured-value`/`:protocol-
min`/`:protocol-max`) are permanent facts already on the engagement.

### 4. Calibration screening reuses the unconditional-evaluation discipline for a further domain

`calibration-not-current-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for a further application in this fleet, for BOTH
`:calibration/screen` and `:certification/issue` -- the SAME shape
`marketadmin.governor/surveillance-flag-unresolved-violations`
establishes for its own domain.

### 5. Single actuation event

`testlab.governor`'s `high-stakes` set has exactly one member
(`:actuation/issue-certification`), matching `6511`'s/`6621`'s/
`6629`'s/`6612`'s/`6492`'s single-actuation shape -- this domain has
one distinct real-world professional act (issuing a certification),
not two.

### 6. Double-certification guard checks a dedicated boolean fact, not `:status` -- deliberately sidestepping `6492`'s lifecycle trap

`already-certified-violations` checks `:certified?`, a dedicated
boolean set once and never cleared, rather than a `:status` value that
could legitimately advance past a checked state (the exact trap
`cloud-itonami-isic-6492`'s ADR-0001 documents in detail, rediscovered
there despite four prior safe reuses, and explicitly avoided BY DESIGN
in `6920`'s and `6611`'s equivalent guards). This actor's `:status`
never needs to encode "has this actuation already happened" at all, so
there is no analogous status-lifecycle risk to fall into here -- a
deliberate architectural choice informed directly by the lesson from
three prior builds, not a re-derivation by shape-analogy (the exact
failure mode that caused `6492`'s bug in the first place).

### 7. No fabricated international certification-number standard

Same discipline as every sibling's registry: there is no single
international check-digit standard for a certification reference
number. `testlab.registry` therefore does not invent one; it validates
required fields and assigns a jurisdiction-scoped sequence number only.

### 8. No bespoke capability lib

Like `6920`, and unlike every other actor in this fleet (each
referencing its own `kotoba-lang/*` capability lib), this vertical's
engagement records are practice-specific rather than a shared cross-
operator data contract -- `testlab.*` runs on the generic identity/
forms/dmn/bpmn/audit-ledger stack only, per the blueprint's own
explicit statement.

### 9. No bug this time

Unlike `6492` (a real status-lifecycle bug) and `6920` (a real
NullPointerException from an unguarded type-specific recompute), this
build's test suite, lint, and demo-ledger verification all passed
clean on the first run -- the `already-certified-violations` design
(Decision 6) and the `out-of-tolerance-violations` pure-recompute shape
(Decision 3) were both DELIBERATELY informed by prior builds' lessons
before writing any code, rather than discovered as bugs after the
fact. The demo (`clojure -M:dev:run`) was still independently verified
against the printed audit ledger -- basis tags `:no-spec-basis` ·
`:out-of-tolerance` (both a below-minimum and an above-maximum case) ·
`:calibration-not-current` · `:already-certified` all appear exactly
where the sim script intends, and the certification history contains
exactly one drafted record after the double-certification attempt is
held -- the same discipline that caught every real bug in this
fleet so far, applied here and finding nothing to fix.

## Consequences

- (+) Technical-testing/analysis gets the same governed, auditable-
  actor treatment as the twelve prior actors, extending the pattern to
  a genuinely different domain (ISIC division 71) for the first time.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/testlab/phase_test.clj`'s
  `certification-issue-never-auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/testlab/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) `within-tolerance?`/`out-of-tolerance-violations` is a genuine
  new check shape for this fleet (two-sided range, not a single-
  direction threshold), regression-tested by `test/testlab/
  governor_contract_test.clj`'s `out-of-tolerance-below-is-held`/
  `out-of-tolerance-above-is-held`.
- (+) The double-certification guard design (Decision 6) demonstrates
  a lesson applied BY DESIGN CHOICE across a THIRD consecutive build
  (`6920`, `6611`, now `7120`), each explicitly informed by `6492`'s
  bug rather than re-derived by shape-analogy.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `testlab.facts/coverage`
  reports this honestly rather than claiming broader coverage.
- (-) `within-tolerance?` models only whether the point measured value
  itself falls within the protocol's own tolerance range, not a full
  measurement-uncertainty analysis (uncertainty budget, expanded-
  uncertainty coverage factor, inter-laboratory proficiency-testing
  requirement are out of scope -- see that fn's own docstring); real
  instrument/lab-information-system integration and ongoing
  proficiency-testing/inter-laboratory comparison programs are all out
  of scope for this OSS actor -- each operator's responsibility (see
  README's coverage table).
- 30 tests / 127 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Keep `cloud-itonami-isic-7120` at `:blueprint` only | ❌ | The standing direction continues past `6611`; technical testing/analysis is a natural, well-precedented next domain, and deliberately outside the finance/insurance thread this fleet had been concentrated in |
| Model `out-of-tolerance-violations` as two separate checks (below-minimum, above-maximum) | ❌ | The underlying real-world concept (a tolerance range) is genuinely one comparison with two bounds, not two independent rules -- `within-tolerance?`'s own docstring frames it as synthesizing this fleet's prior single-direction checks into one |
| Model a full measurement-uncertainty analysis for conformance-test rigor | ❌ | Genuinely more complex real-world metrology that this R0 does not claim to model correctly -- honestly scoped to the point measured value against the protocol's own range instead, same as every sibling's "starting catalog, not exhaustive" posture |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/testing`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning `6920`'s ADR already established |

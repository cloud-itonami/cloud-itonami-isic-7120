# cloud-itonami-isic-7120

Open Business Blueprint for **ISIC Rev.5 7120**: Technical testing and
analysis. This repository publishes a technical-testing/analysis
actor -- engagement intake, jurisdiction accreditation assessment,
instrument-calibration screening and test-result/certification
issuance -- as an OSS business that any qualified, accredited testing
laboratory can fork, deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611)) --
the first technical-testing/professional-scientific-services vertical
(ISIC division 71) in this fleet. Here it is **TestLab-LLM ⊣ Test
Integrity Governor**.

> **Why an actor layer at all?** An LLM is great at drafting an
> engagement summary, normalizing intake, and checking whether a
> measured value actually falls within a test protocol's own tolerance
> range -- but it has **no notion of which jurisdiction's lab-
> accreditation/certification-standard requirements are official, no
> license to issue a real test result or certification, and no way to
> know on its own whether an engagement's instrument calibration is
> actually current**. Letting it issue a certification directly
> invites fabricated jurisdiction citations, a certification issued
> against a measurement outside its own protocol's tolerance range,
> and a stale instrument calibration being quietly waved through -- and
> liability for whoever runs it. This project seals the TestLab-LLM
> into a single node and wraps it with an independent **Test Integrity
> Governor**, a human **approval workflow**, and an immutable **audit
> ledger**.

## Scope: what this actor does and does not do

This actor covers engagement intake through jurisdiction accreditation
assessment, instrument-calibration screening and test-result/
certification issuance. It does **not**, by itself, hold an
accreditation to operate a testing laboratory in any jurisdiction, and
it does not claim to. It also does **not** model a full measurement-
uncertainty analysis -- no uncertainty budget, no expanded-uncertainty
coverage factor, no inter-laboratory proficiency-testing requirement
(see `testlab.registry/within-tolerance?`'s own docstring for the
honest simplification this makes: does the point measured value itself
fall within the protocol's own tolerance range, not whether measurement
uncertainty itself is accounted for). Whoever deploys and operates a
live instance (an accredited testing laboratory) supplies the
jurisdiction-specific accreditation, the real metrology expertise and
the real instrument/lab-information-system integrations, and bears
that jurisdiction's liability -- the software supplies the governed,
spec-cited, audited execution scaffold so that operator does not have
to build the compliance layer from scratch for every new market.

### Actuation

**Issuing a real test result or certification is never autonomous, at
any phase, by construction.** Two independent layers enforce this
(`testlab.governor`'s `:actuation/issue-certification` high-stakes
gate and `testlab.phase`'s phase table, which never puts
`:certification/issue` in any phase's `:auto` set) -- see
`testlab.phase`'s docstring and `test/testlab/phase_test.clj`'s
`certification-issue-never-auto-at-any-phase`. The actor may draft,
check and recommend; a human laboratory director is always the one who
actually issues a certification. Like `6511`/`6621`/`6629`/`6612`/
`6492`, this actor has ONE actuation event.

## The core contract

```
engagement intake + jurisdiction facts (testlab.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ TestLab-LLM  │ ─────────────▶ │ Test Integrity              │  (independent system)
   │  (sealed)    │  + citations    │ Governor: spec-basis ·      │
   └──────────────┘                 │ evidence-incomplete ·        │
                             commit ◀────┼──────────▶ hold │ out-of-tolerance
                                 │             │           │ (two-sided range,
                           record + ledger  escalate ─▶ human   not a single cap) ·
                                             (ALWAYS for         calibration-not-current ·
                                              :certification/     already-certified
                                              issue)
```

**The TestLab-LLM never issues a certification the Test Integrity
Governor would reject, and never does so without a human sign-off.**
Hard violations (fabricated jurisdiction requirements; unsupported
accreditation evidence; a measured value outside its own protocol's
tolerance range; a stale instrument calibration; a double
certification) force **hold** and *cannot* be approved past; a clean
certification proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean lifecycle (certification issuance) + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a sample-handling and
testing robot performs physical sample preparation and instrument
operation, under the actor, gated by the independent **Test Integrity
Governor**. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Test Integrity Governor, certification draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`7120`). Like `6920`, this vertical's engagement records are practice-
specific rather than a shared cross-operator data contract, so
`testlab.*` runs on the generic identity/forms/dmn/bpmn/audit-ledger
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/testlab/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + certification history. No dynamically-filed sub-record -- the actuation op acts directly on a pre-seeded engagement, and the double-certification guard checks a dedicated `:certified?` boolean rather than a `:status` value |
| `src/testlab/registry.cljc` | Certification draft records, plus `within-tolerance?` -- the FIRST check in this fleet to combine a MINIMUM and a MAXIMUM bound in one comparison (a measured value must satisfy `protocol-min <= value <= protocol-max`) |
| `src/testlab/facts.cljc` | Per-jurisdiction lab-accreditation/certification-standard catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/testlab/testlabllm.cljc` | **TestLab-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/calibration-screening/certification-issuance proposals |
| `src/testlab/governor.cljc` | **Test Integrity Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · out-of-tolerance, pure ground-truth two-sided-range recompute · calibration-not-current, unconditional evaluation) + already-certified guard + 1 soft (confidence/actuation gate) |
| `src/testlab/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (certification always human; engagement intake is the ONLY auto-eligible op, no capital risk) |
| `src/testlab/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/testlab/sim.cljc` | demo driver |
| `test/testlab/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers engagement intake through jurisdiction accreditation
assessment, instrument-calibration screening and test-result/
certification issuance -- the core governed lifecycle this blueprint's
own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Engagement intake + per-jurisdiction lab-accreditation/certification-standard checklisting, HARD-gated on an official spec-basis citation (`:engagement/intake`/`:jurisdiction/assess`) | A full measurement-uncertainty analysis (uncertainty budget, expanded-uncertainty coverage factor, inter-laboratory proficiency-testing requirement -- see `within-tolerance?`'s docstring) |
| Instrument-calibration screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:calibration/screen`) | Real instrument/lab-information-system integration, tax/regulatory reporting |
| Certification issuance, HARD-gated on the measured value falling within its own protocol's tolerance range and a double-certification guard (`:certification/issue`) | Ongoing proficiency-testing / inter-laboratory comparison programs themselves |
| Immutable audit ledger for every intake/assessment/screening/certification decision | |

Extending coverage is additive: add the next gate (e.g. a measurement-
uncertainty check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`testlab.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `testlab.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `testlab.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `TestLab-LLM` + `Test Integrity Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the twelve
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.

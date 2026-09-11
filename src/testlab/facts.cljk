(ns testlab.facts
  "Per-jurisdiction testing-laboratory accreditation/certification-
  standard regulatory catalog -- the G2-style spec-basis table the
  Test Integrity Governor checks every jurisdiction/assess proposal
  against ('did the advisor cite an OFFICIAL public source for this
  jurisdiction's lab-accreditation/certification-standard
  requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official accreditation
  body (see `:provenance`); they are a STARTING catalog, not a from-
  scratch survey of all ~194 jurisdictions. Extending coverage is
  additive: add one map to `catalog`, cite a real source, done --
  never invent a jurisdiction's requirements to make coverage look
  bigger.

  Unlike most prior catalogs, the underlying accreditation STANDARD
  (ISO/IEC 17025, general requirements for the competence of testing
  and calibration laboratories) is genuinely INTERNATIONAL -- each
  jurisdiction's national accreditation body (NITE/A2LA/UKAS/DAkkS)
  administers ISO/IEC 17025 accreditation locally, but the underlying
  competence standard itself is the same international document across
  all four seeded jurisdictions.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  chain-of-custody/test-protocol/calibration-certificate/accreditation
  evidence set submitted in some form; `:legal-basis` / `:owner-
  authority` / `:provenance` are the G2 citation the governor requires
  before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "独立行政法人製品評価技術基盤機構 (National Institute of Technology and Evaluation, NITE)"
          :legal-basis "計量法 (Measurement Act) + JIS Q 17025 (ISO/IEC 17025の日本産業規格)"
          :national-spec "NITE 試験所認定制度 (JNLA/IAJapan)"
          :provenance "https://www.nite.go.jp/"
          :required-evidence ["サンプル管理記録 (chain-of-custody record)"
                              "試験方法引用書 (test-protocol citation)"
                              "校正証明書 (calibration certificate)"
                              "試験所認定証明書 (laboratory accreditation certificate)"]}
   "USA" {:name "United States"
          :owner-authority "American Association for Laboratory Accreditation (A2LA) / National Institute of Standards and Technology (NIST)"
          :legal-basis "ISO/IEC 17025 (General requirements for the competence of testing and calibration laboratories)"
          :national-spec "A2LA Accreditation Requirements (ISO/IEC 17025-based)"
          :provenance "https://www.a2la.org/ https://www.nist.gov/"
          :required-evidence ["Chain-of-custody record"
                              "Test-protocol citation"
                              "Calibration certificate"
                              "Laboratory accreditation certificate"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "United Kingdom Accreditation Service (UKAS)"
          :legal-basis "ISO/IEC 17025 (as administered by UKAS)"
          :national-spec "UKAS Accreditation Requirements"
          :provenance "https://www.ukas.com/"
          :required-evidence ["Chain-of-custody record"
                              "Test-protocol citation"
                              "Calibration certificate"
                              "Laboratory accreditation certificate"]}
   "DEU" {:name "Germany"
          :owner-authority "Deutsche Akkreditierungsstelle (DAkkS)"
          :legal-basis "DIN EN ISO/IEC 17025 (Akkreditierungsstellengesetz, AkkStelleG)"
          :national-spec "DAkkS Akkreditierungsanforderungen"
          :provenance "https://www.dakks.de/"
          :required-evidence ["Probenverfolgungsprotokoll (chain-of-custody record)"
                              "Prüfverfahrensangabe (test-protocol citation)"
                              "Kalibrierschein (calibration certificate)"
                              "Akkreditierungsurkunde (laboratory accreditation certificate)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to issue a
  certification on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-7120 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `testlab.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

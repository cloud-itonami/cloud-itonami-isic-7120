(ns testlab.store
  "SSoT for the technical-testing/analysis actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/testlab/store_contract_test.clj), which is the whole point:
  the actor, the Test Integrity Governor and the audit ledger never
  know which SSoT they run on.

  Like `credit.store`'s/`accounting.store`'s/`marketadmin.store`'s
  simpler entities, an ENGAGEMENT is acted on directly by the ONE
  actuation op -- no dynamically-filed sub-record, and the double-
  issuance guard checks a dedicated `:certified?` boolean rather than
  a `:status` value, the same discipline `accounting.governor`'s/
  `marketadmin.governor`'s guards establish.

  The ledger stays append-only on every backend: 'which engagement was
  screened for current instrument calibration, which test result/
  certification was issued, on what jurisdictional basis, approved by
  whom' is always a query over an immutable log -- the audit trail a
  client trusting a laboratory needs, and the evidence an operator
  needs if a certification is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [testlab.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (engagement [s id])
  (all-engagements [s])
  (calibration-of [s engagement-id] "committed calibration screening verdict for an engagement, or nil")
  (assessment-of [s engagement-id] "committed jurisdiction accreditation assessment, or nil")
  (ledger [s])
  (certification-history [s] "the append-only certification history (testlab.registry drafts)")
  (next-sequence [s jurisdiction] "next certification-number sequence for a jurisdiction")
  (engagement-already-certified? [s engagement-id] "has this engagement already been certified?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-engagements [s engagements] "replace/seed the engagement directory (map id->engagement)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained engagement set so the actor + tests run
  offline."
  []
  {:engagements
   {"engagement-1" {:id "engagement-1" :client "Sakura Materials K.K." :sample-description "steel alloy sample A-1"
                     :test-protocol :tensile-strength :measured-value 500 :protocol-min 400 :protocol-max 600
                     :calibration-current? true :certified? false :jurisdiction "JPN" :status :intake}
    "engagement-2" {:id "engagement-2" :client "Atlantis Composites Ltd." :sample-description "polymer sample B-2"
                     :test-protocol :tensile-strength :measured-value 450 :protocol-min 400 :protocol-max 600
                     :calibration-current? true :certified? false :jurisdiction "ATL" :status :intake}
    "engagement-3" {:id "engagement-3" :client "鈴木部品工業" :sample-description "steel alloy sample C-3"
                     :test-protocol :tensile-strength :measured-value 350 :protocol-min 400 :protocol-max 600
                     :calibration-current? true :certified? false :jurisdiction "JPN" :status :intake}
    "engagement-4" {:id "engagement-4" :client "田中素材" :sample-description "steel alloy sample D-4"
                     :test-protocol :tensile-strength :measured-value 650 :protocol-min 400 :protocol-max 600
                     :calibration-current? true :certified? false :jurisdiction "JPN" :status :intake}
    "engagement-5" {:id "engagement-5" :client "佐藤化学" :sample-description "steel alloy sample E-5"
                     :test-protocol :tensile-strength :measured-value 500 :protocol-min 400 :protocol-max 600
                     :calibration-current? false :certified? false :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- issue-certification!
  "Backend-agnostic `:engagement/mark-certified` -- looks up the
  engagement via the protocol and drafts the certification record, and
  returns {:result .. :engagement-patch ..} for the caller to
  persist."
  [s engagement-id]
  (let [e (engagement s engagement-id)
        seq-n (next-sequence s (:jurisdiction e))
        result (registry/register-certification engagement-id (:jurisdiction e) seq-n)]
    {:result result
     :engagement-patch {:certified? true
                        :certification-number (get result "certification_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (engagement [_ id] (get-in @a [:engagements id]))
  (all-engagements [_] (sort-by :id (vals (:engagements @a))))
  (calibration-of [_ id] (get-in @a [:calibration id]))
  (assessment-of [_ engagement-id] (get-in @a [:assessments engagement-id]))
  (ledger [_] (:ledger @a))
  (certification-history [_] (:certifications @a))
  (next-sequence [_ jurisdiction] (get-in @a [:sequences jurisdiction] 0))
  (engagement-already-certified? [_ engagement-id] (boolean (get-in @a [:engagements engagement-id :certified?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (swap! a update-in [:engagements (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :calibration/set
      (swap! a assoc-in [:calibration (first path)] payload)

      :engagement/mark-certified
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (issue-certification! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:sequences jurisdiction] (fnil inc 0))
                       (update-in [:engagements engagement-id] merge engagement-patch)
                       (update :certifications registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-engagements [s engagements] (when (seq engagements) (swap! a assoc :engagements engagements)) s))

(defn seed-db
  "A MemStore seeded with the demo engagement set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :calibration {} :ledger [] :sequences {}
                           :certifications []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/calibration payloads, ledger facts,
  certification records) are stored as EDN strings so `langchain.db`
  doesn't expand them into sub-entities -- the same convention every
  sibling actor's store uses."
  {:engagement/id               {:db/unique :db.unique/identity}
   :assessment/engagement-id    {:db/unique :db.unique/identity}
   :calibration/engagement-id   {:db/unique :db.unique/identity}
   :ledger/seq                  {:db/unique :db.unique/identity}
   :certification/seq           {:db/unique :db.unique/identity}
   :sequence/jurisdiction       {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- engagement->tx [{:keys [id client sample-description test-protocol measured-value protocol-min protocol-max
                              calibration-current? certified? jurisdiction status certification-number]}]
  (cond-> {:engagement/id id}
    client                        (assoc :engagement/client client)
    sample-description             (assoc :engagement/sample-description sample-description)
    test-protocol                   (assoc :engagement/test-protocol test-protocol)
    measured-value                   (assoc :engagement/measured-value measured-value)
    protocol-min                      (assoc :engagement/protocol-min protocol-min)
    protocol-max                       (assoc :engagement/protocol-max protocol-max)
    (some? calibration-current?)        (assoc :engagement/calibration-current? calibration-current?)
    (some? certified?)                   (assoc :engagement/certified? certified?)
    jurisdiction                          (assoc :engagement/jurisdiction jurisdiction)
    status                                 (assoc :engagement/status status)
    certification-number                    (assoc :engagement/certification-number certification-number)))

(def ^:private engagement-pull
  [:engagement/id :engagement/client :engagement/sample-description :engagement/test-protocol
   :engagement/measured-value :engagement/protocol-min :engagement/protocol-max
   :engagement/calibration-current? :engagement/certified? :engagement/jurisdiction
   :engagement/status :engagement/certification-number])

(defn- pull->engagement [m]
  (when (:engagement/id m)
    {:id (:engagement/id m) :client (:engagement/client m) :sample-description (:engagement/sample-description m)
     :test-protocol (:engagement/test-protocol m) :measured-value (:engagement/measured-value m)
     :protocol-min (:engagement/protocol-min m) :protocol-max (:engagement/protocol-max m)
     :calibration-current? (boolean (:engagement/calibration-current? m))
     :certified? (boolean (:engagement/certified? m))
     :jurisdiction (:engagement/jurisdiction m) :status (:engagement/status m)
     :certification-number (:engagement/certification-number m)}))

(defrecord DatomicStore [conn]
  Store
  (engagement [_ id]
    (pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id id])))
  (all-engagements [_]
    (->> (d/q '[:find [?id ...] :where [?e :engagement/id ?id]] (d/db conn))
         (map #(pull->engagement (d/pull (d/db conn) engagement-pull [:engagement/id %])))
         (sort-by :id)))
  (calibration-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?eid
                :where [?k :calibration/engagement-id ?eid] [?k :calibration/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ engagement-id]
    (dec* (d/q '[:find ?p . :in $ ?eid
                :where [?a :assessment/engagement-id ?eid] [?a :assessment/payload ?p]]
              (d/db conn) engagement-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (certification-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :certification/seq ?s] [?e :certification/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :sequence/jurisdiction ?j] [?e :sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (engagement-already-certified? [s engagement-id]
    (boolean (:certified? (engagement s engagement-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :engagement/upsert
      (d/transact! conn [(engagement->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/engagement-id (first path) :assessment/payload (enc payload)}])

      :calibration/set
      (d/transact! conn [{:calibration/engagement-id (first path) :calibration/payload (enc payload)}])

      :engagement/mark-certified
      (let [engagement-id (first path)
            {:keys [result engagement-patch]} (issue-certification! s engagement-id)
            jurisdiction (:jurisdiction (engagement s engagement-id))
            next-n (inc (next-sequence s jurisdiction))]
        (d/transact! conn
                     [(engagement->tx (assoc engagement-patch :id engagement-id))
                      {:sequence/jurisdiction jurisdiction :sequence/next next-n}
                      {:certification/seq (count (certification-history s)) :certification/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-engagements [s engagements]
    (when (seq engagements) (d/transact! conn (mapv engagement->tx (vals engagements)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:engagements ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [engagements]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-engagements s engagements))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo engagement set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))

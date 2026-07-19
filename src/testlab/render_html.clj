(ns testlab.render-html
  "Build-time HTML renderer for docs/samples/operator-console.html.
  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300).
  Drives the REAL actor stack (testlab.operation -> testlab.governor -> testlab.store).
  No invented numbers, no timestamps, byte-identical across reruns."
  (:require [clojure.string :as str]
            [testlab.store :as store]
            [testlab.operation :as op]
            [testlab.phase :as phase]
            [testlab.governor :as governor]
            [langgraph.graph :as g]))

(def ^:private operator {:actor-id "op-1" :actor-role :laboratory-director :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn run-demo!
  "Drives the real OperationActor StateGraph through a scenario built
  directly from `testlab.store/demo-data` and `testlab.governor`'s
  actual rules (this repo's `testlab.sim` was run and cross-checked
  against the real seed data / governor rules and found trustworthy --
  its ids/ops match, so this mirrors the same scenario rather than
  reusing sim.cljc's -main directly, to keep this namespace's demo
  self-contained):

    1. `:engagement/intake` engagement-1 -- clean, phase-3 auto-commit
       (`testlab.phase`'s only auto-eligible op).
    2. `:jurisdiction/assess` engagement-1 (JPN has a real spec-basis in
       `testlab.facts`) -- escalates (not yet auto-eligible at any
       phase) -> human approval -> commit.
    3. `:calibration/screen` engagement-1 -- clean, likewise escalates
       -> human approval -> commit.
    4. `:certification/issue` engagement-1 -- a `governor/high-stakes`
       op, ALWAYS escalates even when clean -> human approval -> commit
       (drafts a real certification record via `testlab.registry`).
    5. `:jurisdiction/assess` engagement-2 with `:no-spec?` --
       engagement-2's own seeded jurisdiction is \"ATL\", which has NO
       entry in `testlab.facts/catalog` -- HARD hold, rule
       `:no-spec-basis`.
    6. `:jurisdiction/assess` engagement-3 (JPN, has spec-basis) ->
       commit, then `:certification/issue` engagement-3 -- engagement-3
       is seeded with `:measured-value 350` below its own
       `:protocol-min 400` -- HARD hold, rule `:out-of-tolerance`.
    7. `:jurisdiction/assess` engagement-4 (JPN, has spec-basis) ->
       commit, then `:certification/issue` engagement-4 -- engagement-4
       is seeded with `:measured-value 650` above its own
       `:protocol-max 600` -- HARD hold, rule `:out-of-tolerance`
       (the other tolerance-bound direction).
    8. `:calibration/screen` engagement-5 -- engagement-5 is seeded with
       `:calibration-current? false` -- HARD hold, rule
       `:calibration-not-current`.
    9. `:certification/issue` engagement-1 AGAIN -- engagement-1 was
       already certified in step 4 -- HARD hold, rule
       `:already-certified`.

  Returns the seeded `db` (a `testlab.store/MemStore`) after the run,
  so `render` can read every value straight off it."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "t1" {:op :engagement/intake :subject "engagement-1"
                       :patch {:id "engagement-1" :client "Sakura Materials K.K."}})

    (exec! actor "t2" {:op :jurisdiction/assess :subject "engagement-1"})
    (approve! actor "t2")

    (exec! actor "t3" {:op :calibration/screen :subject "engagement-1"})
    (approve! actor "t3")

    (exec! actor "t4" {:op :certification/issue :subject "engagement-1"})
    (approve! actor "t4")

    (exec! actor "t5" {:op :jurisdiction/assess :subject "engagement-2" :no-spec? true})

    (exec! actor "t6" {:op :jurisdiction/assess :subject "engagement-3"})
    (approve! actor "t6")
    (exec! actor "t7" {:op :certification/issue :subject "engagement-3"})

    (exec! actor "t8" {:op :jurisdiction/assess :subject "engagement-4"})
    (approve! actor "t8")
    (exec! actor "t9" {:op :certification/issue :subject "engagement-4"})

    (exec! actor "t10" {:op :calibration/screen :subject "engagement-5"})

    (exec! actor "t11" {:op :certification/issue :subject "engagement-1"})

    db))

;; ----------------------------- render helpers -----------------------------

(defn- esc
  "Minimal HTML-escape -- every rendered string passes through this."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for
  "The most recent ledger fact for `subject-id`, off the real
  subject-key field this repo's `commit-fact`/`hold-fact` records use:
  `:subject` (see `testlab.operation/commit-fact` and
  `testlab.governor/hold-fact`)."
  [ledger subject-id]
  (last (filter #(= subject-id (:subject %)) ledger)))

(defn- status-cell
  "[css-class label] for the last known ledger fact of a subject --
  the same cond pattern used fleet-wide."
  [fact]
  (cond
    (nil? fact)                                 ["muted" "in progress"]
    (= :committed (:t fact))                    ["ok" "committed"]
    (= :approval-granted (:t fact))              ["ok" "approval-granted"]
    (= :governor-hold (:t fact))                 ["err" (str "governor-hold: " (str/join "," (map name (:basis fact))))]
    (= :approval-rejected (:t fact))             ["err" "approval-rejected"]
    (= :approval-requested (:t fact))            ["warn" "approval-requested"]
    :else                                        ["muted" "in progress"]))

(defn- engagements-table [db]
  (let [engagements (store/all-engagements db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>id</th><th>client</th><th>sample</th><th>test protocol</th><th>measured</th>\n"
     "<th>tolerance range</th><th>jurisdiction</th><th>calibration current?</th><th>certified?</th><th>status</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [e engagements
            :let [ledger (store/ledger db)
                  fact (last-fact-for ledger (:id e))
                  [cls label] (status-cell fact)]]
        (str "<tr>"
             "<td><code>" (esc (:id e)) "</code></td>"
             "<td>" (esc (:client e)) "</td>"
             "<td>" (esc (:sample-description e)) "</td>"
             "<td><code>" (esc (:test-protocol e)) "</code></td>"
             "<td>" (esc (:measured-value e)) "</td>"
             "<td>[" (esc (:protocol-min e)) "," (esc (:protocol-max e)) "]</td>"
             "<td>" (esc (:jurisdiction e)) "</td>"
             "<td>" (if (:calibration-current? e) "yes" "<span class=\"critical\">no</span>") "</td>"
             "<td>" (if (:certified? e) (str "yes (" (esc (:certification-number e)) ")") "no") "</td>"
             "<td class=\"" cls "\">" (esc label) "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- committed-records-table [db]
  (let [certifications (store/certification-history db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>record_id</th><th>kind</th><th>engagement_id</th><th>jurisdiction</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [r certifications]
        (str "<tr>"
             "<td><code>" (esc (get r "record_id")) "</code></td>"
             "<td>" (esc (get r "kind")) "</td>"
             "<td><code>" (esc (get r "engagement_id")) "</code></td>"
             "<td>" (esc (get r "jurisdiction")) "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- action-gate-table
  "Static op-contract description, sourced from the real
  `testlab.phase/phases` (phase 3, this actor's `default-phase`) and
  `testlab.governor/high-stakes` -- not invented, just rendered."
  []
  (let [ph (get phase/phases phase/default-phase)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>op</th><th>phase-" phase/default-phase " write allowed?</th><th>auto-eligible?</th><th>always escalates (high-stakes)?</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [op (sort phase/write-ops)]
        (str "<tr>"
             "<td><code>" (esc op) "</code></td>"
             "<td>" (if (contains? (:writes ph) op) "yes" "<span class=\"warn\">no</span>") "</td>"
             "<td>" (if (contains? (:auto ph) op) "<span class=\"ok\">yes</span>" "no") "</td>"
             "<td>" (if (contains? governor/high-stakes op) "<span class=\"critical\">yes</span>" "no") "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- audit-ledger-table [db]
  (str
   "<table>\n<thead><tr>\n"
   "<th>t</th><th>op</th><th>subject</th><th>disposition</th><th>basis / rule</th>\n"
   "</tr></thead>\n<tbody>\n"
   (str/join
    "\n"
    (for [f (store/ledger db)]
      (str "<tr>"
           "<td>" (esc (:t f)) "</td>"
           "<td><code>" (esc (:op f)) "</code></td>"
           "<td><code>" (esc (:subject f)) "</code></td>"
           "<td class=\""
           (case (:disposition f) :commit "ok" :hold "err" "muted")
           "\">" (esc (:disposition f)) "</td>"
           "<td>" (if (seq (:basis f))
                    (str/join ", " (map (comp esc name) (:basis f)))
                    "&mdash;")
           "</td>"
           "</tr>")))
   "\n</tbody></table>"))

(def ^:private css
  "table { width: 100%; border-collapse: collapse; font-size: 14px; }
.ok { color: #137a3f; }
body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }
header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }
h2 { margin-top: 0; font-size: 15px; }
.warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }
main { max-width: 980px; margin: 24px auto; padding: 0 20px; }
header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }
.muted { color: #888; font-size: 13px; }
.critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }
.card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }
th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }
header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }
code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }")

(defn render [db]
  (str
   "<!doctype html>\n"
   "<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
   "<title>testlab.render-html -- Test Integrity Governor operator console</title>\n"
   "<style>\n" css "\n</style>\n"
   "</head>\n<body>\n"
   "<header class=\"bar\"><h1>Test Integrity Governor -- Operator Console</h1>"
   "<span class=\"badge\">ISIC 7120 &middot; phase " phase/default-phase " (" (:label (get phase/phases phase/default-phase)) ")</span>"
   "</header>\n"
   "<main>\n"
   "<div class=\"card\">\n<h2>Engagements</h2>\n" (engagements-table db) "\n</div>\n"
   "<div class=\"card\">\n<h2>Committed records (certification-issuance drafts)</h2>\n" (committed-records-table db) "\n</div>\n"
   "<div class=\"card\">\n<h2>Action gate (testlab.phase &middot; testlab.governor/high-stakes)</h2>\n" (action-gate-table) "\n</div>\n"
   "<div class=\"card\">\n<h2>Audit ledger</h2>\n" (audit-ledger-table db) "\n</div>\n"
   "</main>\n"
   "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out)))

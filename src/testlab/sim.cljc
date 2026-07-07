(ns testlab.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean engagement through
  intake -> jurisdiction accreditation assessment -> calibration
  screening -> certification-issuance proposal (always escalates) ->
  human approval -> commit, then shows five HARD holds (a jurisdiction
  with no spec-basis, a measured value below its own protocol's
  tolerance range, a measured value above its own protocol's tolerance
  range, a stale instrument calibration, and a double-certification of
  an already-certified engagement) that never reach a human at all,
  and prints the audit ledger + the draft certification records."
  (:require [langgraph.graph :as g]
            [testlab.store :as store]
            [testlab.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :laboratory-director :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== engagement/intake engagement-1 (JPN, tensile-strength, clean; 500 within [400,600]) ==")
    (println (exec! actor "t1" {:op :engagement/intake :subject "engagement-1"
                                :patch {:id "engagement-1" :client "Sakura Materials K.K."}} operator))

    (println "== jurisdiction/assess engagement-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "engagement-1"} operator))
    (println (approve! actor "t2"))

    (println "== calibration/screen engagement-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :calibration/screen :subject "engagement-1"} operator))
    (println (approve! actor "t3"))

    (println "== certification/issue engagement-1 (always escalates -- actuation/issue-certification) ==")
    (let [r (exec! actor "t4" {:op :certification/issue :subject "engagement-1"} operator)]
      (println r)
      (println "-- human laboratory director approves --")
      (println (approve! actor "t4")))

    (println "== jurisdiction/assess engagement-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t5" {:op :jurisdiction/assess :subject "engagement-2" :no-spec? true} operator))

    (println "== jurisdiction/assess engagement-3 (escalates -- human approves; sets up the below-tolerance test) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "engagement-3"} operator))
    (println (approve! actor "t6"))

    (println "== certification/issue engagement-3 (measured-value 350 below protocol-min 400 -> HARD hold) ==")
    (println (exec! actor "t7" {:op :certification/issue :subject "engagement-3"} operator))

    (println "== jurisdiction/assess engagement-4 (escalates -- human approves; sets up the above-tolerance test) ==")
    (println (exec! actor "t8" {:op :jurisdiction/assess :subject "engagement-4"} operator))
    (println (approve! actor "t8"))

    (println "== certification/issue engagement-4 (measured-value 650 above protocol-max 600 -> HARD hold) ==")
    (println (exec! actor "t9" {:op :certification/issue :subject "engagement-4"} operator))

    (println "== calibration/screen engagement-5 (stale instrument calibration -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t10" {:op :calibration/screen :subject "engagement-5"} operator))

    (println "== certification/issue engagement-1 AGAIN (double-certification of an already-certified engagement -> HARD hold) ==")
    (println (exec! actor "t11" {:op :certification/issue :subject "engagement-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft certification records ==")
    (doseq [r (store/certification-history db)] (println r))))

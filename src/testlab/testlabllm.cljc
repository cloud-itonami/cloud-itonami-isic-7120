(ns testlab.testlabllm
  "TestLab-LLM client -- the *contained intelligence node* for the
  technical-testing/analysis actor.

  It normalizes engagement intake, drafts a per-jurisdiction lab-
  accreditation/certification-standard evidence checklist, screens
  engagements for a stale instrument calibration, and drafts the test-
  result/certification-issuance action. CRITICAL: it is a smart-but-
  untrusted advisor. It returns a *proposal* (with a rationale + the
  fields it cited), never a committed record or a real certification.
  Every output is censored downstream by `testlab.governor` before
  anything touches the SSoT, and `:certification/issue` proposals
  NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/issue-certification | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [testlab.facts :as facts]
            [testlab.registry :as registry]
            [testlab.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the client, sample description/measured value or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "案件記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :engagement/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction lab-accreditation/certification-standard evidence
  checklist draft. `:no-spec?` injects the failure mode we must defend
  against: proposing a checklist for a jurisdiction with NO official
  spec-basis in `testlab.facts` -- the Test Integrity Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [e (store/engagement db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction e))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "testlab.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-calibration
  "Instrument-calibration screening draft. `:calibration-current?` on
  the engagement record injects the failure mode: the Test Integrity
  Governor must HOLD, un-overridably, on any stale calibration."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)]
    (cond
      (nil? e)
      {:summary "対象engagementが見つかりません" :rationale "no engagement record"
       :cites [] :effect :calibration/set :value {:engagement-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (false? (:calibration-current? e))
      {:summary    (str (:client e) ": 校正切れの試験装置を検出")
       :rationale  "スクリーニングが校正切れの試験装置を検出。人手確認とホールドが必須。"
       :cites      [:calibration-check]
       :effect     :calibration/set
       :value      {:engagement-id subject :verdict :not-current}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:client e) ": 校正は最新")
       :rationale  "校正スクリーニング非該当。"
       :cites      [:calibration-check]
       :effect     :calibration/set
       :value      {:engagement-id subject :verdict :current}
       :stake      nil
       :confidence 0.9})))

(defn- propose-certification
  "Draft the actual CERTIFICATION-ISSUANCE action -- issuing a real
  test result/certification. ALWAYS `:stake :actuation/issue-
  certification` -- this is a REAL-WORLD act (a client/regulator/
  downstream buyer may rely on it), never a draft the actor may auto-
  run. See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`testlab.phase`); the governor also always escalates on
  `:actuation/issue-certification`. Two independent layers agree,
  deliberately."
  [db {:keys [subject]}]
  (let [e (store/engagement db subject)
        tolerant? (and e (registry/within-tolerance? e))]
    {:summary    (str subject " 向け試験結果証明書発行提案"
                      (when e (str " (client=" (:client e) ")")))
     :rationale  (if e
                   (str "measured-value=" (:measured-value e)
                        " range=[" (:protocol-min e) "," (:protocol-max e) "]")
                   "engagementが見つかりません")
     :cites      (if e [subject] [])
     :effect     :engagement/mark-certified
     :value      {:engagement-id subject}
     :stake      :actuation/issue-certification
     :confidence (if tolerant? 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :engagement/intake      (normalize-intake db request)
    :jurisdiction/assess       (assess-jurisdiction db request)
    :calibration/screen           (screen-calibration db request)
    :certification/issue             (propose-certification db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは試験・分析ラボの試験結果証明エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:engagement/upsert|:assessment/set|:calibration/set|"
       ":engagement/mark-certified) "
       ":stake(:actuation/issue-certification か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess  {:engagement (store/engagement st subject)}
    :calibration/screen   {:engagement (store/engagement st subject)}
    :certification/issue  {:engagement (store/engagement st subject)}
    {:engagement (store/engagement st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Test Integrity Governor
  escalates/holds -- an LLM hiccup can never auto-issue a
  certification."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :testlabllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})

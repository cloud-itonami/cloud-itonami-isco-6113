(ns nurseryops.advisor
  "Nursery Operations Advisor — proposing a nursery/horticultural-site
  scheduling/logistics coordination operation (log a
  planting/pruning/harvest-record entry, schedule a crew operation,
  flag a safety concern, coordinate a plants/seeds/agrochemicals
  supply order) from a crew roster, nursery-site registration and
  safety-reporting policy. Swappable mock/llm; the advisor ONLY
  proposes — `nurseryops.governor` independently gates every proposal
  and always escalates safety concerns and above-threshold supply
  orders. The advisor never proposes to directly finalize a
  cultivation-execution decision (e.g. a specific planting, pruning or
  pesticide-application timing/technique) or to override a nursery
  safety officer's judgment — those stay permanently out of this
  actor's scope. Modeled on cloud-itonami-isco-7111's
  housebuilder.advisor for the physical-safety-domain shape.

  A proposal: {:op :log-work-record|:schedule-crew-operation|
               :flag-safety-concern|:coordinate-supply-order
               :effect :propose :grower-id str :nursery-id str
               :cost number :hazard-type kw :task str :stake kw
               :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op grower-id nursery-id hazard-type]
  (case op
    :log-work-record
    (str "logged work record for grower " grower-id " at nursery " nursery-id)

    :schedule-crew-operation
    (str "scheduled crew operation for planting task at nursery " nursery-id)

    :flag-safety-concern
    (str "flagged " (name (or hazard-type :hazard)) " concern for grower "
         grower-id " at nursery " nursery-id " — routed for nursery safety officer review")

    :coordinate-supply-order
    (str "coordinated supply order for grower " grower-id " at nursery " nursery-id)

    (str "proposed " (name op) " for grower " grower-id " at nursery " nursery-id)))

(defn- infer [_store {:keys [op stake grower-id nursery-id cost hazard-type task]
                       :as request}]
  {:op op
   :effect :propose
   :grower-id grower-id
   :nursery-id nursery-id
   :cost cost
   :hazard-type hazard-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op grower-id nursery-id hazard-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a nursery/horticultural-site scheduling/logistics
   coordination advisor. Given a request, propose an :op (one of
   :log-work-record, :schedule-crew-operation, :flag-safety-concern,
   :coordinate-supply-order), the :grower-id, :nursery-id, and any
   :cost/:hazard-type/:task fields, an honest :confidence and a
   :stake. Never propose an op outside this closed list, and never
   propose to directly finalize a cultivation-execution decision
   (e.g. deciding to proceed with a specific planting, pruning or
   pesticide-application timing/technique), or to override a nursery
   safety officer's judgment — those are always out of this actor's
   scope; it coordinates nursery scheduling/logistics only and never
   performs cultivation work or applies agrochemicals itself. Safety
   concerns (chemical exposure, equipment hazards) always require
   human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))

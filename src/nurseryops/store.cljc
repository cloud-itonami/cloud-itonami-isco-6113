(ns nurseryops.store
  "SSoT for the ISCO-08 6113 nursery/horticultural-site scheduling and
  logistics coordination actor (itonami actor pattern, ADR-2607121000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a nursery
  scheduling/logistics coordination robot performs crew scheduling,
  planting/pruning/harvest-record data logging and
  plants/seeds/agrochemicals supply-order coordination for a
  gardening/horticultural/nursery-grower crew under this
  advisor/governor pair, which never dispatches hardware itself, never
  performs cultivation work itself, and never finalizes a
  cultivation-execution decision (e.g. a specific planting, pruning or
  pesticide-application timing/technique) or overrides a nursery safety
  officer's judgment — those remain the nursery safety officer's
  exclusive judgment). Modeled on cloud-itonami-isco-7111's
  housebuilder.store for the physical-safety-domain shape (this
  domain's own physical-safety dimension: pesticide/fertilizer
  chemical-exposure risk and cultivation-tool/equipment hazards).

  Domain:

    grower  — a registered gardening/horticultural/nursery-grower crew
              member (:grower-id, :name)
    nursery — a registered nursery/horticultural site {:nursery-id
              :name :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged
              planting/pruning/harvest-record entry, a scheduled crew
              operation, a flagged safety concern, or a coordinated
              plants/seeds/agrochemicals supply order) — written ONLY
              via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (grower [s grower-id])
  (nursery [s nursery-id])
  (records-of [s grower-id])
  (ledger [s])
  (register-grower! [s g])
  (register-nursery! [s n])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (grower [_ grower-id] (get-in @a [:growers grower-id]))
  (nursery [_ nursery-id] (get-in @a [:nurseries nursery-id]))
  (records-of [_ grower-id] (filter #(= grower-id (:grower-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-grower! [s g]
    (swap! a assoc-in [:growers (:grower-id g)] g) s)
  (register-nursery! [s n]
    (swap! a assoc-in [:nurseries (:nursery-id n)] n) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:growers {} :nurseries {} :records [] :ledger []}
                                    seed)))))

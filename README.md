# cloud-itonami-isco-6113

Open Occupation Blueprint for **ISCO-08 6113**: Gardeners, Horticultural and Nursery Growers.

This repository designs a forkable OSS business for a nursery/horticultural-site scheduling and logistics coordination practice: a nursery scheduling and supply-coordination robot manages crew/task records under a governor-gated actor, so a gardening/horticultural/nursery-grower crew keeps its own operating records instead of renting a closed workforce-management SaaS.

**Maturity: `:implemented`.** `src/nurseryops/` implements the
`NurseryOperationsActor` as a `langgraph.graph/state-graph`
(`nurseryops.actor`) wired to a `Nursery Operations Advisor`
(`nurseryops.advisor`) and an independent `NurseryOperationsGovernor`
(`nurseryops.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 21 tests / 45 assertions green (`clojure -M:test`).
HARD invariants (always hold, never overridable): grower provenance,
nursery provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a cultivation-execution decision
(e.g. deciding to proceed with a specific planting, pruning or
pesticide-application timing/technique) or override a nursery safety
officer's judgment. Always-escalate paths (human sign-off regardless of
confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a nursery scheduling/logistics coordination robot performs crew scheduling, planting/pruning/harvest-record data logging and plants/seeds/agrochemicals supply-order coordination for a gardening/horticultural/nursery-grower crew, under an actor that proposes actions and an independent **Nursery Operations Governor** that gates them. The governor never
dispatches hardware itself, never performs cultivation work on the nursery site, and never finalizes a cultivation-execution decision or overrides a nursery safety officer's judgment; `:high`/`:safety-critical` actions (such as a flagged chemical-exposure/equipment-hazard concern, or an above-threshold supply order) require human sign-off. **This actor coordinates nursery scheduling/logistics only — it never performs cultivation work itself.**

## Core Contract

```text
crew roster + nursery-site registration + safety-reporting policy
        |
        v
Nursery Operations Advisor -> Nursery Operations Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a cultivation-execution decision, override a nursery safety officer's
judgment, suppress an operating record, or disclose sensitive data
without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `6113`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.

# Context, problem and objectives

The cost of fragmentation shows up in three places: time spent building traceability matrices by hand, rework caused by upstream changes that were never propagated, and the effort of preparing audit evidence.

**Why now.** Three forces make the problem urgent. Programmes adopt MBSE and multiply the models that must stay aligned; certification bodies demand reproducible evidence rather than evidence reconstructed after the fact; and the arrival of MCP and agents makes data access almost free, shifting value from whoever connects to whoever guarantees identity, history and rules.

**Why a suite is not enough.** Vendor suites integrate their own tools well and other vendors' tools poorly, while real programmes always use more than one. The missing layer is vendor-neutral: that is what the accelerator provides.


## Problems to solve

1. No reliable answer to "which components are affected by this requirement change?" without manual work.
2. Cross-tool links exist as document numbers pasted into text fields — neither verifiable nor navigable.
3. Compliance matrices are rebuilt at every revision, at a cost linear in the number of requirements.
4. No historical memory: the state of traceability at a past date cannot be reconstructed.
5. Existing point-to-point integrations are N×N, fragile, and break at every tool upgrade.

## Measurable objectives

| Objective | Metric | Baseline | Target |
| ------------------------ | ------------------------ | -------------- | ---------- |
| Reduce traceability effort | hours/month on manual matrices | to be measured | −70% |
| Make links navigable | % links with a resolvable URI | ~0% | >95% |
| Accelerate impact analysis | mean time per analysis | hours | <5 min |
| Cut audit cost | days preparing evidence | to be measured | −50% |
| Contain integration cost | person-days per new tool | n/a | <20 |

Baselines must be measured in phase 0. Without an initial measurement the targets are not defensible.

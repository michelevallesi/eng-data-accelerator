# ADR-0023: Graph-wide SHACL conformance (FR-M06) is deferred to increment 2

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

FR-M06 asks for the graph to be validated against SHACL shapes with violations
reported. The requirement is **Should** and is not assigned to any story in the
increment-1 backlog. SHACL already runs in two places in the walking skeleton:
at adapter emission time against resource shapes (FR-A08) and in the Link Index
during the `asserted → validated` transition against maturity shapes (FR-S09).
Adding a third periodic job over the full graph would duplicate validation
work and does not change the demo outcome.

## Decision

- In increment 1, SHACL runs at adapter emission (FR-A08) and in the Link Index
  on the `asserted → validated` transition (FR-S09). Both reuse the same shape
  library (`shared/eda-shacl`).
- Graph-wide periodic SHACL conformance (FR-M06) is deferred to increment 2.
  When it is added, it reuses the same shape library and reports violations
  through the same mechanism used in increment 1.

## Alternatives considered

- Run full-graph SHACL on every projection write: too expensive for the walking
  skeleton and does not catch anything the two above points do not catch.
- Run SHACL only in the projection service and drop it from the adapter and
  Link Index: loses the fail-fast property on invalid resources and the
  deterministic link-state transition.

## Consequences

- The projection service does not expose a SHACL endpoint in increment 1.
- The shape library lives in a shared module so increment 2 can reuse it
  without migration.

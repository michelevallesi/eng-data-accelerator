# ADR-0016: Platform-owned state in PostgreSQL; link state changes as events

- **Status:** Proposed
- **Date:** 2026-09-25

## Context

The graph is derived and rebuildable (AD-01, FR-M03). Some state, however, is created by
the platform itself and cannot be derived from any tool: if it lived only in the graph, a
rebuild would lose it. Triple stores are also weak at transactional uniqueness.

## Decision

1. **PostgreSQL holds the state the platform owns:**
   - the Resolver identity map: opaque URI ↔ tool + native ID, aliases, tombstones, with a
     unique constraint guaranteeing one URI per artefact, minted once (AD-03, FR-F02);
   - Link Index metadata: link endpoints, relation, current state, who confirmed it and
     when (FR-F01, FR-S05);
   - adapter operational state: TRS cursors, idempotency keys, dead-letter bookkeeping,
     transactional outbox (FR-B04, FR-B05).
2. **Every change to that state is also published as a CloudEvent** through the outbox:
   `eda.uri.minted`, `eda.uri.aliased`, `eda.uri.tombstoned`, `eda.link.created`,
   `eda.link.state-changed` (with previous state, new state, cause, actor).
3. The projection service consumes these events like any other. A graph rebuild therefore
   reproduces URIs **and** link states exactly.
4. PostgreSQL is **not** a store for artefact content or for query answering. Anything a
   user queries across tools comes from the graph.

## Alternatives considered

- Everything in the graph: loses platform-owned state on rebuild; no reliable uniqueness.
- Everything as events only (pure event sourcing): valid, but point lookups and uniqueness
  on minting become complex; PostgreSQL + outbox gives the same replayability more simply.

## Consequences

- PostgreSQL is backed up as system-of-record data (DR-04); the graph is not required to be.
- Tests assert that rebuild after replay yields identical link states (extends S06).
- Writes to PostgreSQL and the event outbox happen in one transaction.

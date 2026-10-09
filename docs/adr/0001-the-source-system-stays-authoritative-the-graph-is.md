# ADR-0001: The source system stays authoritative; the graph is a derived, rebuildable projection

- **Status:** Accepted — from PRD v1.0 (AD-01)
- **Source:** `docs/prd/` chapter "Architectural decisions"

## Decision

The source system stays authoritative; the graph is a derived, rebuildable projection

## Rationale

Avoids a second master and makes the graph disposable.

## Consequences for implementation

Any code, schema or configuration that contradicts this decision must be raised as a new
ADR with status `Proposed`, not implemented directly.

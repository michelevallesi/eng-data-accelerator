# ADR-0013: Identical containers for cloud and on-premise, with isolated installation

- **Status:** Accepted — from PRD v1.0 (AD-13)
- **Source:** `docs/prd/` chapter "Architectural decisions"

## Decision

Identical containers for cloud and on-premise, with isolated installation

## Rationale

Regulated contexts cannot depend on external services.

## Consequences for implementation

Any code, schema or configuration that contradicts this decision must be raised as a new
ADR with status `Proposed`, not implemented directly.

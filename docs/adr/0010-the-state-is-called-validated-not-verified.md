# ADR-0010: The state is called *validated*, not *verified*

- **Status:** Accepted — from PRD v1.0 (AD-10)
- **Source:** `docs/prd/` chapter "Architectural decisions"

## Decision

The state is called *validated*, not *verified*

## Rationale

Avoids a clash with V&V verification.

## Consequences for implementation

Any code, schema or configuration that contradicts this decision must be raised as a new
ADR with status `Proposed`, not implemented directly.

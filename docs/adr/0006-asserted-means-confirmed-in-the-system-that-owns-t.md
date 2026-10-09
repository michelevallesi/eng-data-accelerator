# ADR-0006: *Asserted* means confirmed in the system that owns the relationship

- **Status:** Accepted — from PRD v1.0 (AD-06)
- **Source:** `docs/prd/` chapter "Architectural decisions"

## Decision

*Asserted* means confirmed in the system that owns the relationship

## Rationale

The platform validates relationships, it does not own them.

## Consequences for implementation

Any code, schema or configuration that contradicts this decision must be raised as a new
ADR with status `Proposed`, not implemented directly.

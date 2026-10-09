# ADR-0012: Everything that varies between programmes is configuration, not code

- **Status:** Accepted — from PRD v1.0 (AD-12)
- **Source:** `docs/prd/` chapter "Architectural decisions"

## Decision

Everything that varies between programmes is configuration, not code

## Rationale

It is the condition for being an accelerator rather than a bespoke project.

## Consequences for implementation

Any code, schema or configuration that contradicts this decision must be raised as a new
ADR with status `Proposed`, not implemented directly.

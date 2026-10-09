# ADR-0024: FR-G scope in increment 1 is limited to FR-G17

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

The increment-1 backlog lists the FR-G requirements (data governance) in **Out**.
However, the demo that closes the increment depends on FR-G17: before
configuration control, a new version of an input must mark dependent links as
`superseded` and open dispositions for the owners. This is implemented by S07.
Without an explicit scoping ADR, "FR-G is out" and "the demo needs FR-G17" look
contradictory.

## Decision

- Increment 1 implements FR-G17 (pre-release notification/disposition regime)
  through the disposition machinery in S07.
- FR-G01–G16 remain **Out** of increment 1. In particular: no PLM backbone
  anchor (FR-G01), no publication envelope (FR-G04), no promotion patterns
  (FR-G05, G06), no evidence-of-record protection (FR-G07), no Change hook
  (FR-G08), no custody patterns (FR-G09), no event pinning beyond what S06
  already does (FR-G10).
- The two regimes are mutually exclusive: FR-G17 applies before release;
  FR-G08 applies after. Increment 1 covers only the first.

## Alternatives considered

- Keep FR-G strictly out, including FR-G17: breaks the closing demo and
  removes the only link-state transition that matters for the walking
  skeleton (`asserted → superseded` with a disposition).
- Pull in FR-G07 and FR-G08 as well: requires the Change concept, which
  requires a PLM backbone anchor (FR-G01), which the backlog explicitly
  defers.

## Consequences

- S07 implements dispositions and the `superseded` transition.
- The e2e scenario (S11) exercises the FR-G17 regime: a REQ-019 revision opens
  dispositions for LC-11 and TC-219 owners.
- Any future story that touches released evidence must raise a new ADR before
  implementation; the walking skeleton has no mechanism to protect released
  evidence.

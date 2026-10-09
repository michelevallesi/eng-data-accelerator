# ADR-0022: Fixture–PRD scenario divergence on E-403 is deliberate

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

PRD annex A describes `E-403 → 400-1180/A` as a dangling link.
`fixtures/skid400/links.json` marks the same link as `proposed`, because
`400-1180/A` exists in `fixtures/skid400/artefacts.json`. `fixtures/README.md`
already documents the drift and announces the compensation: the dangling case
is produced in tests by deleting `TC-211` through the Polarion simulator (S03).
Without an ADR, this divergence looks like a bug and a well-intentioned future
contributor may "fix" it by editing the fixture, which would invalidate the
UUIDs already seeded into the Resolver.

## Decision

- Fixtures are the authoritative input for every walking-skeleton test.
- PRD annex A describes a programme state at a point in time, not a test oracle.
- The dangling case is produced by the test harness in S03 (delete TC-211) and
  verified by S07 and S09.
- Fixtures do not change in increment 1. Changing a fixture that already carries
  pre-minted UUIDs requires a migration note and an alias ADR, because the
  Resolver treats the UUIDs as opaque identifiers minted once (AD-03).

## Alternatives considered

- Edit the fixtures to match PRD annex A verbatim: invalidates the UUIDs
  already seeded, forces the Resolver to re-mint and breaks the "minted once"
  invariant.
- Edit the PRD to match the fixtures: possible as a future minor revision; out
  of scope for an implementation ADR.

## Consequences

- When a test needs a dangling link, it uses `REQ-052 → TC-211` after the S03
  deletion. The link `E-403 → 400-1180/A` is used as the canonical `proposed`
  case.
- A future PRD revision may align the annex text with the fixtures. If it does,
  this ADR is marked `Superseded by` that revision.

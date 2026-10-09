# ADR-0021: Impact analysis API — shape, defaults and OpenAPI

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

FR-C02 requires impact analysis configurable by depth and relationship type and
leaves the response shape and the direction (upstream vs downstream) open.
`fixtures/skid400/expected/impact-REQ-019-depth3.json` pins a shape used by S09's
acceptance test. Without a decision, every consumer (cockpit, future GraphQL
facade, agents over MCP) risks diverging on field names.

## Decision

**Endpoint.**

```
GET /impact/{uri}?depth=<n>&relations=<csv>&direction=<downstream|upstream>
```

- `depth`: integer, default `3`, maximum `10`.
- `relations`: comma-separated relation names (fixture conventions in increment 1:
  `satisfiedBy`, `allocatedTo`, `realisedBy`, `analysedBy`, `verifiedBy`,
  `documentedBy`). Default: all permitted outbound relations for the resource type.
- `direction`: default `downstream`.
- `uri`: either a concept URI or a version URI. A concept URI is resolved to its
  current version URI before traversal.

**Response.** Matches `fixtures/skid400/expected/impact-REQ-019-depth3.json`:
`root`, `direction`, `depth`, `partial`, `nodes[]` with `nativeId`, `uri`,
`depth`, `path`, `viaRelation`, `linkState`, `flagged`. A sibling array
`dangling[]` is added, carrying links to deleted targets (FR-F06); dangling
links are reported but not traversed. `proposed` links set `flagged: true` on
the node and traversal stops at that node unless `relations` explicitly
includes a proposed relation.

**Partial results.** When any source adapter is marked unavailable (FR-A10) at
request time, `partial: true` and every node whose provenance leads back to that
adapter carries `age` (ISO 8601 duration) and `stale: true`. The response is
never silently truncated.

**OpenAPI.** The service publishes OpenAPI 3.1 at `/openapi.yaml` (FR-C06).

## Alternatives considered

- GraphQL facade instead of a REST endpoint: deferred — the backlog lists FR-M09
  in **Out** for increment 1.
- Return a flat node list without `path`: makes the cockpit tree view
  reconstruct paths client-side, which is doable but duplicates work.
- Make `direction` required (no default): more explicit, but the fixture
  expected result is `downstream` and every downstream user we know of wants it.

## Consequences

- The response shape is a public contract from increment 1 onwards; adding a
  field is backward-compatible, removing or renaming one is a breaking change
  that requires a new ADR.
- Depth and relation-type filters are enforced server-side; a malformed value
  returns `400` with a `Problem+JSON` body.

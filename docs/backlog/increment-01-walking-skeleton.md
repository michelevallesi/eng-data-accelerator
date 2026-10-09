# Increment 1 — Walking skeleton

## Goal

Prove the whole chain end to end on the SKID-400 fixtures, with the thinnest possible
implementation of every plane: a change in a (simulated) tool becomes an event, the event
becomes a projection, the projection answers an impact question, and link states react.

**Demo that closes the increment:** in the Polarion simulator, REQ-019 changes from
17.2 bar to 21 bar. Within one minute the graph holds the new version with provenance,
the links leaving REQ-019 are `superseded`, dispositions are open for the owners of
LC-11 and TC-219, and the impact view for REQ-019 lists
the downstream artefacts, including PSV-418, with link states and data age.

## Scope

**In:** Resolver, Polarion simulator + pass-through adapter, Teamcenter simulator +
API-backed adapter, event bus, projection service, Link Index with state machine, SHACL
maturity checks, impact analysis API, one minimal cockpit page.

**Out (later increments):** Capella, COMOS and Ansys adapters · OSLC global configuration
(FR-F03) beyond tagging data with `GC-2026-Q3` · publication, promotion and Change
(FR-G) · "superseded input" on published results and
evidence exclusion (FR-S14 for results, FR-S16), which need publication · Terminology Service beyond the maturity scale · GraphQL facade (FR-M09) · real
OIDC (keep the identity-propagation seam, FR-A07) · Helm charts · AI and agents.

## Exit criteria

1. `make e2e` runs the demo above on Docker Compose with no manual steps.
2. `make rebuild-graph` produces a graph identical to the live one (same triple set).
3. Stopping the Teamcenter simulator makes impact results **explicitly partial**, with age.
4. No test or service contacts anything outside the Compose network.
5. Every story below is done according to the Definition of Done.

## Definition of Done

Code + tests green in `make verify` · FR/AD IDs referenced in tests · README/CLAUDE.md
commands updated · no new dependency without ADR-0015 coverage · short demo note in the PR.

## Status at this update

S00–S03 done · S04 in progress · S05–S11 to do. Close R02 and R03 while finishing S04,
then R01, before starting S05.

## Retrofit stories (do before S05)

These bring already-implemented stories in line with ADR-0016 and FR-S13. Each may already
be satisfied: check first, then implement only what is missing.

### R01 — Resolver events (retrofit of S02, ADR-0016)
- AC: minting, aliasing and tombstoning each produce one `eda.uri.*` CloudEvent, committed
  in the same transaction as the PostgreSQL write (transactional outbox).
- AC: replaying those events into an empty Resolver store reproduces the identity map.

### R02 — Adapter operational state (retrofit of S04, ADR-0016)
- AC: TRS cursor, idempotency keys and dead-letter bookkeeping live in PostgreSQL, not in
  memory or local files.
- AC: killing and restarting the adapter mid-poll resumes with no duplicate and no lost event.

### R03 — Version identity (retrofit of S03 and S04, prerequisite of FR-S13)
- AC: every update in the Polarion simulator creates a new immutable version identifier,
  exposed on the resource and in the TRS change event.
- AC: each CloudEvent carries both the concept URI and the version identifier; S06 and S07
  depend on it.

## Stories (in order)

### S00 — Repository scaffold
Maven multi-module layout, Dockerfile per service (DR-01), `docker-compose.yml` with Kafka
(KRaft), Fuseki, PostgreSQL; `Makefile` with the targets listed in CLAUDE.md.
- AC: `make verify` and `make up` succeed on a clean machine with only Docker and JDK 21.
- AC: images are tagged with the git SHA.

### S01 — Fixture library
A small module that loads `fixtures/*/` (tools, artefacts, links, configurations,
maturity scale) and is shared by simulators and tests.
- AC: loads SKID-400 and AERO-7; fails loudly on unknown fields or dangling references.

### S02 — Resolver (FR-F02, AD-03)
Mint opaque URIs for `(tool, nativeId)`, resolve URI → native ID + deep link, record
aliases on rename, tombstones on delete. Seed from `fixtures/*/uris.json`. PostgreSQL with outbox events `eda.uri.*` (ADR-0016).
- AC: minting twice for the same `(tool, nativeId)` returns the same URI.
- AC: renaming 400-1105/C keeps its URI; the name is only an attribute.
- AC: a deleted artefact resolves to a tombstone (HTTP 410), not 404.
- AC: no code path builds a URI from an attribute (enforced by a test on the minting API).

### S03 — Polarion simulator (FR-A01, FR-A02, FR-A03, FR-A09, FR-A11)
Minimal OSLC RM Core 3.0 server over the Polarion fixtures: Service Provider Catalog,
requirement and test-case resources in JSON-LD and Turtle, TRS base + change log, and a
test-only mutation endpoint (update, delete).
- AC: `GET` on REQ-019 returns JSON-LD by default and Turtle on content negotiation.
- AC: updating REQ-019 appends a TRS change event; deleting TC-211 appends a deletion.
- AC: every update creates a new immutable version identifier, exposed on the resource and
  in the TRS change event (prerequisite of FR-S13).

### S04 — Polarion pass-through adapter (FR-A08, FR-A10, FR-A12, FR-B04, FR-B05, FR-B06, FR-B09, FR-B10)
Poll the TRS, validate each resource against SHACL, emit CloudEvents to Kafka keyed by
resource URI, with provenance attributes. Retries with backoff, dead-letter topic.
- AC: the REQ-019 update produces exactly one CloudEvent with source, type, subject
  (URI), version and time; replaying the poll does not duplicate it (idempotency).
- AC: an invalid resource goes to the dead-letter topic and can be reprocessed.
- AC: with the simulator down, the adapter reports `unavailable`; it never emits partial data.
- AC: TRS cursor, idempotency keys and dead-letter bookkeeping live in PostgreSQL
  (ADR-0016); a restart mid-poll resumes with no duplicate and no lost event.
- AC: each CloudEvent carries both the concept URI and the version identifier.

### S05 — Teamcenter simulator and API-backed adapter
REST simulator over the Teamcenter fixtures (items, revisions, change feed); adapter
mapping them to OSLC-shaped resources and CloudEvents, same contract as S04.
- AC: revising 400-1105/C to a new revision emits one event with the new version URI.

### S06 — Projection service (FR-M01, FR-M02, FR-M03, FR-M05, FR-M08)
Consume CloudEvents, write one named graph per resource version into Fuseki with PROV-O,
keep version history, expose data age per node. `rebuild` replays the topic from offset 0.
- AC: after the REQ-019 update both versions are queryable; the current one is flagged.
- AC: rebuild from scratch yields the same triple set as the live graph (hash compare),
  including URIs and link states (ADR-0016).
- AC: every projected triple can be traced to the event that produced it.

### S07 — Link Index, link state machine and dispositions (FR-F01, FR-F04, FR-F05, FR-F06, FR-S05, FR-S07, FR-S08, FR-S13, FR-S15, CR-25)
Store link metadata only, in PostgreSQL (ADR-0016); seed from `fixtures/*/links.json`; create links from either
side; derive the inverse view; apply state transitions on events.
- AC: a new version of a link's source or target sets the link to `superseded`.
- AC: deleting a target sets the link to `dangling`; the link is kept.
- AC: every link records the version of both endpoints when asserted (FR-S13).
- AC: a `superseded` link opens a disposition for the owner resolved from
  `fixtures/*/owners.json` (CR-25); outcomes: confirmed on the new version, updated,
  to be rerun, not applicable. "Confirmed" re-asserts the link on the new version.
- AC: dispositions are platform-owned state (PostgreSQL + events, ADR-0016).
- AC: `proposed → asserted` requires an explicit confirmation call carrying a user identity.
- AC: every state transition is written with its `eda.link.state-changed` event in one
  transaction (outbox), with previous state, new state, cause and actor (ADR-0016).

### S08 — SHACL maturity checks (FR-S02, FR-S09, FR-S10, FR-M06)
Load the canonical maturity scale from `fixtures/*/maturity-scale.json` (configuration,
not code). Shapes with configurable severity; misalignment = downstream more mature than
upstream **on the same axis** (lifecycle with lifecycle, fitness-for-use with fitness).
- AC: an asserted, conformant link becomes `validated`; a non-conformant one stays
  `asserted` with the violation reported.
- AC: a synthetic test link from a `Draft` requirement to a `Released` component is
  reported as a misalignment; the rule's severity changes via configuration only.

### S09 — Impact analysis API (FR-C02, FR-C06, FR-A10, FR-M08)
`GET /impact/{uri}?depth=&relations=` over the graph, downstream or upstream, returning
nodes, paths, link states and data age; OpenAPI documented. Proposed links are returned
but flagged; dangling links are reported, not traversed.
- AC: the result for REQ-019, depth 3, matches `fixtures/skid400/expected/impact-REQ-019-depth3.json`.
- AC: with a source adapter down, the response carries `partial: true` and the age of
  every stale node.

### S10 — Minimal cockpit page (FR-C04 subset)
One page served by the query service: search an artefact, show its impact, link states,
misalignments, freshness badges and the list of open dispositions. No framework needed in this increment.
- AC: the demo scenario is fully visible on this page without using any other tool.

### S11 — End-to-end scenario (`make e2e`)
Automates the demo: start stack, load fixtures, mutate REQ-019, wait for projection,
assert superseded links, open dispositions and impact result, stop the Teamcenter simulator, assert partial.
- AC: green in under five minutes on a developer laptop.

## Notes

- The PRD's aggregate figures (e.g. "14 artefacts" for UC-02) refer to the full project of
  about 1,200 artefacts. The fixtures are a representative subset: expected results are
  computed from the fixtures themselves and reviewed by the product owner.
- Link relation names (`satisfiedBy`, `allocatedTo`, `realisedBy`, `analysedBy`,
  `verifiedBy`) are fixture conventions; the ontology is owned by the Terminology board.

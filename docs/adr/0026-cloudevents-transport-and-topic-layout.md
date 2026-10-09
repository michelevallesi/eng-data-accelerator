# ADR-0026: CloudEvents transport convention and Kafka topic layout

- **Status:** Proposed
- **Date:** 2026-10-01 (renumbered from ADR-0017 on 2026-10-07; ADR-0017 is now
  reserved for the publication-kit decision)

## Context

FR-B09, FR-B10 and FR-B11 require every change to flow as CloudEvents over Kafka,
with the schema published as a versioned AsyncAPI specification. The walking-skeleton
stories S04, S05 and S06 all depend on topic names, keys and event shapes being stable
before any code is written. Nothing in the PRD or prior ADRs fixes them.

## Decision

**Topics.**

| Topic | Payload | Partition key |
| --- | --- | --- |
| `eda.<tool>.changes` | `eda.resource.changed` | resource URI |
| `eda.<tool>.dlq` | poison messages with original headers | original key |
| `eda.uri.minted` / `eda.uri.aliased` / `eda.uri.tombstoned` | Resolver state events | URI |
| `eda.link.created` / `eda.link.state-changed` | Link Index state events | link id |
| `eda.disposition.opened` / `eda.disposition.decided` | Disposition events | link id |

Keying by resource URI gives per-URI ordering (FR-B06). `<tool>` is a stable
lowercase token declared in `fixtures/*/tools.json`.

**Mandatory CloudEvents attributes.** `specversion=1.0`, `source` (adapter id),
`type` (as above), `subject` (resource URI), `id` (UUIDv4), `time` (adapter clock),
`datacontenttype`. Extensions: `edaversion` (resource revision), `edamappingversion`
(mapping used to produce the payload — FR-M02), `edasourcetime` (modification time
in the source system, when the source exposes it), `edaactor` (user id when the
change was user-driven, FR-A07 propagation seam).

**Payload.** JSON-LD for `eda.resource.changed`; plain JSON for platform-state
events. All payloads are described by JSON Schemas referenced from the AsyncAPI
document at [docs/specs/asyncapi.yaml](../specs/asyncapi.yaml).

**AsyncAPI.** `docs/specs/asyncapi.yaml`, versioned. A schema registry is
deferred to increment 2; until then, consumers rely on the AsyncAPI document plus
the semantic versioning of its schemas (FR-B11).

## Alternatives considered

- One Kafka topic for everything: simplifies partitioning but breaks selective
  replay for S06 rebuild and makes consumer groups bleed across concerns.
- Topic per event type across every tool: too many topics, no benefit over
  `<tool>.changes` because the `type` attribute already discriminates.
- Avro or Protobuf payloads: better for schema evolution, but adds a registry and
  tooling to the walking skeleton. Deferred.

## Consequences

- Changing topic names after increment 1 is a breaking change and requires a new
  ADR plus a migration plan.
- Topic retention is sized per topic and declared in `docker-compose.yml`;
  `eda.*.changes`, `eda.uri.*` and `eda.link.*` retain for the duration of the
  walking skeleton so that `make rebuild-graph` can replay from offset 0.

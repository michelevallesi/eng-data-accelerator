# ADR-0015: Technology stack for the walking skeleton

- **Status:** Proposed — to be confirmed by the product owner before S00 starts
- **Date:** 2026-09-25

## Context

Increment 1 must prove the full chain (adapter → bus → projection → link states → impact)
with the fewest moving parts, while staying compatible with the PRD's target architecture
(OSLC via Eclipse Lyo, CloudEvents over Kafka, RDF + SHACL, OCI containers).

## Decision

| Concern | Choice | Why |
| --- | --- | --- |
| Language, build | Java 21, Maven multi-module | Eclipse Lyo is Java; one toolchain for all services |
| Service framework | Spring Boot 3 | mature, well known, good Testcontainers support |
| OSLC | Eclipse Lyo OSLC4J (latest stable, pinned) | AD-14: Lyo is the OSLC implementation of record |
| RDF and SHACL | Apache Jena (incl. Jena SHACL) | one library for parsing, SPARQL and validation |
| Triple store | Apache Jena Fuseki (TDB2) as default; GraphDB Enterprise as production option | see *Triple store evaluation* below |
| Event bus | Apache Kafka (KRaft, single node in dev) + CloudEvents Java SDK | FR-B09 |
| Relational store | PostgreSQL | Resolver identity map and Link Index metadata |
| Tests | JUnit 5, Testcontainers, fixtures | no real tool is ever contacted |
| Publication kit (client) | Python, see ADR-0017 | engineers' desktops, OMPython; services stay Java |
| Packaging | Dockerfile per service, Docker Compose | Helm charts (DR-02) come in increment 2 |
| Cockpit | static page served by the query service | avoids a second toolchain in increment 1 |

## Triple store evaluation

Requirements from the PRD: SPARQL 1.1 with named graphs per resource version (FR-M05),
access control on the endpoint (FR-M04), fast full rebuild (FR-M03), isolated on-premise
installation (DR-03), and a cost that works when the accelerator is deployed at many
customers.

| Option | Fit | Main limit | Verdict |
| --- | --- | --- | --- |
| Apache Jena Fuseki (TDB2) | open source, same library as SHACL validation, trivial in containers and air-gapped sites | no native HA cluster; basic access control | **default** |
| GraphDB Enterprise (Graphwise) | Raft HA cluster, Kafka connector, strong tooling | per-core licence per node, per customer | **production option** |
| GraphDB Free | easy to try | two concurrent queries; licence forbids for-profit services | not for delivery |
| Stardog | virtual graphs, reasoning, on-premise or cloud | platform-only offering; virtual graphs overlap the federated plane (lock-in risk) | not selected |
| Eclipse RDF4J | open source, Java | same class as Fuseki, no gain in scale or HA | fallback to Fuseki |
| Virtuoso | very fast on large volumes | heavier operations | evaluate only with a benchmark |
| Amazon Neptune | managed service | AWS only, violates DR-03 | excluded |
| Neo4j | excellent traversal, path queries and graph algorithms; neosemantics imports/exports RDF and validates SHACL | property graph: no SPARQL endpoint, no native named graphs; cluster and fine-grained access control only in Enterprise | **optional secondary projection** |
| OrientDB | multi-model, Apache 2.0, community maintained | no RDF support; distributed module being rewritten; declining adoption | excluded |
| Oxigraph, QLever | promising, lightweight or fast | not mature on access control and HA | watch list |

**Why the choice is low-regret.** The graph is a derived projection (AD-01): changing the
store means replaying the event log, not migrating data. Fuseki's lack of an HA cluster is
compensated the same way: a replica is rebuilt from events.

**Rule that keeps it reversible.** Use only standard SPARQL 1.1 and SHACL. No vendor rule
languages, virtual graphs or proprietary full-text syntax in services or shapes.

**Secondary projections.** CQRS allows more than one read model fed by the same events.
A property-graph store such as Neo4j may be added for a specific recurring question (deep
impact, graph algorithms, visual exploration) only if: a measured question justifies it,
it consumes the same events, it is rebuildable, and it is never used as evidence. The RDF
store remains the reference projection.

## Other alternatives considered

Redpanda (Kafka-compatible, lighter) · Python services (faster to write, but splits the
toolchain away from Lyo) · React cockpit (deferred, not rejected).

## Consequences

- The triple store sits behind a repository interface with a contract-test suite; every
  supported store must pass it.
- The choice of store is per programme configuration; licences for GraphDB are the
  customer's, sized per node and core.
- A benchmark on a synthetic programme (about 1,200 artefacts × versions) runs before
  increment 2 and decides whether Fuseki suffices for the first programmes.

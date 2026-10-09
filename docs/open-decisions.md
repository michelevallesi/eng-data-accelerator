# Open decisions

| # | Decision | Needed | Default in the kit |
| --- | --- | --- | --- |
| 1 | Confirm the stack in ADR-0015 | before S00 | Java 21, Spring Boot, Lyo, Jena/Fuseki, Kafka, PostgreSQL |
| 2 | Triple store for production: confirm Fuseki or GraphDB Enterprise after the benchmark in ADR-0015 | before increment 2 | Fuseki behind a repository interface |
| 3 | Repository hosting and CI (GitHub, GitLab, Azure DevOps) | before S00 | `make` targets, CI added in S00 |
| 4 | Teamcenter access for the real adapter (SOA, REST, MCP) and target version | before increment 2 | simulated REST in increment 1 |
| 5 | Polarion version and OSLC availability on the target instance | before increment 2 | OSLC RM simulator |
| 6 | Identity provider (OIDC/SAML) and identity propagation model | before increment 2 | dev stub with a propagation seam (FR-A07) |
| 7 | Link relation vocabulary (names, direction) | during increment 1 | fixture conventions: satisfiedBy, allocatedTo, realisedBy, analysedBy, verifiedBy, documentedBy |
| 8 | Canonical maturity scale per programme | during increment 1 | `fixtures/*/maturity-scale.json` |
| 9 | Language for code comments and docs | before S00 | English |
| 10 | Data classification and export-control rules for the repository | before S00 | fixtures only, no customer data |
| 11 | Accept ADR-0016 (PostgreSQL for platform-owned state, link state events) | before S02 | Proposed |
| 12 | Accept ADR-0017 (publication kit in Python; desktop tools only via publication) | before increment 2 | Proposed |
| 13 | Where controlled templates are stored and who approves them | before P05 | template registry in programme configuration |
| 14 | How MATLAB bindings are tested (licence in CI or scripted manual check) | before P06 | REST contract tests plus manual check |
| 15 | Accept ADR-0026 (CloudEvents transport and Kafka topic layout; renumbered from the collision at 0017) | before increment 2 | Proposed; already in force in S02/S04/S05 |
| 16 | Accept ADR-0027 (defer shared adapter-core / simulator-core extraction until a third adapter) | during increment 1 | Proposed |
| 17 | ADR-0018 concept/version URI split: upstream adapters still emit a single URI per event (Polarion versions same URI with `edaversion` bumps; Teamcenter mints a fresh URI per revision). S06 derives the (concept, version) pair from `(ce_subject, edaversion)` and writes `<subject>#concept` + `<subject>#v/<edaversion>` named graphs. **S07 inherits this limitation**: a link pinned to the Teamcenter revision URI `V1` is NOT superseded when a new URI `V2` arrives, because the link index has no identity bridge between them. The Polarion case (same URI, bumped `edaversion`) is handled fully. A proper ADR-0018 refactor touching both adapters is deferred. | before increment 2 | S06 / S07 projection convention |
| 18 | S07 Link Index fixture seeding at startup is not driven by events, so it is not strictly part of the rebuildable chain. Current behaviour: on `ApplicationReadyEvent`, if `link_link` is empty, seed from `fixtures/<scenario>/links.json`. For a prod rebuild, an admin endpoint or a replay of the resolver's URI events + the first `eda.link.state-changed` events is needed. | before increment 2 | Fixture seed; add admin seed endpoint later |

When a decision is closed, record it as an ADR (or update the one named) and note the ADR number here.

# Non-functional requirements

| Category | Requirement | Target |
| ------------------ | ------------------------------------ | -------------------- |
| Federated query latency | resolve and open a link | < 2 s at p95 |
| Graph query latency | impact analysis at depth 3 | < 5 s at p95 |
| GraphQL facade latency | application view query | < 1 s at p95 |
| Propagation latency | from tool change to a CloudEvents event | < 10 s at p95 |
| Propagation latency | from tool change to updated graph | < 60 s at p95 |
| Scale | triples in the graph | 100 million |
| Scale | indexed artefacts | 5 million |
| Scale | concurrent users | 200 |
| Throughput | peak events per second | 500 |
| Availability | federated plane | 99.5% during business hours |
| Availability | materialised plane | 99.0%, degradation acceptable |
| Rebuild | full graph rebuild | < 8 hours |
| Recovery | RPO / RTO | 24 h / 4 h |

**Controlled degradation.** If the graph is unavailable, federation keeps working: links remain navigable and creatable. If an adapter is unavailable, the graph serves projected data explicitly marked unverified. The system must never present a partial result without saying so.

**Portability.** No lock-in to a single triple store: application queries use standard SPARQL 1.1. Proprietary extensions, where used, are isolated in a replaceable layer.

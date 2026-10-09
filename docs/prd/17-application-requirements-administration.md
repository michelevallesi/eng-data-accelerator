# Application requirements — Administration

## Operations (AR-01…07)

- **AR-01** — Health status per adapter: last event received, lag against the source, error rate.
- **AR-02** — Start, pause and restart synchronisation of a single adapter without affecting others.
- **AR-03** — Rebuild the graph for a single tool or entirely, with a duration estimate.
- **AR-04** — Inspect the dead letter queue, view payload and error, reprocess or discard.
- **AR-05** — Force a full reconciliation to detect divergences and missed deletions.
- **AR-06** — Maintenance mode per tool, with a message visible to end users.
- **AR-07** — Simulate a configuration change in a sandbox before promoting to production.

## Observability (AR-08…12)

- **AR-08** — Metrics on propagation latency, event throughput, graph size, query time.
- **AR-09** — Distributed tracing end to end, from user call to source system.
- **AR-10** — Configurable alerts on lag thresholds, errors and SHACL violations.
- **AR-11** — Data quality reports: dangling links, orphan artefacts, failed mappings.
- **AR-12** — Audit log searchable by user, resource, period and operation type.

## Security and lifecycle (AR-13…18)

- **AR-13** — Role and permission management for the platform, distinct from tool permissions.
- **AR-14** — Rotate adapter credentials without service interruption.
- **AR-15** — Manage consent and ACL propagation into the graph.
- **AR-16** — Selectively delete projected data for GDPR or export control compliance.
- **AR-17** — Backup and restore of graph and Link Index, with stated RPO and RTO.
- **AR-18** — Adapter lifecycle management: versions, compatibility, deprecation.

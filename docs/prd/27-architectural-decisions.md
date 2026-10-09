# Architectural decisions

The decisions that shape the platform, with their rationale. Each can be revised by the programme's architecture board, not by a single project.

| ID | Decision | Rationale |
| --- | ----------------------------- | ----------------------------- |
| AD-01 | The source system stays authoritative; the graph is a derived, rebuildable projection | avoids a second master and makes the graph disposable |
| AD-02 | Separate the federated plane from the materialised plane | truth is read live, cross-cutting questions from the projection |
| AD-03 | Opaque, stable URIs, never derived from attributes | part numbers, codes and paths change; links must survive |
| AD-04 | MCP as first choice for access, OSLC for link semantics | MCP is widespread among vendors; OSLC remains the standard for links and configuration |
| AD-05 | The PLM backbone is the product, configuration and Change anchor | the product is the context in which every relationship makes sense |
| AD-06 | *Asserted* means confirmed in the system that owns the relationship | the platform validates relationships, it does not own them |
| AD-07 | Two ingestion modes: continuous projection and intentional publication | governed platforms are followed, working data is not |
| AD-08 | The evidence of record stays in the product system | the graph assembles and cites, it does not certify |
| AD-09 | Maturity is a constraint on the relationship, spread across six axes | a single field does not survive release gates |
| AD-10 | The state is called *validated*, not *verified* | avoids a clash with V&V verification |
| AD-11 | AI enters after the foundation, read-only; proposals stay proposals | reproducibility, completeness and security come before productivity |
| AD-12 | Everything that varies between programmes is configuration, not code | it is the condition for being an accelerator rather than a bespoke project |
| AD-13 | Identical containers for cloud and on-premise, with isolated installation | regulated contexts cannot depend on external services |
| AD-14 | Eclipse Lyo is a technology constraint, managed as a risk | it is the only mature OSLC implementation, with a small community |

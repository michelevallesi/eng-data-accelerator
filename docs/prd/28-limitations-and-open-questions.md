# Limitations and open questions

## Structural limitations

- **The economic threshold.** Below five tools, point-to-point is cheaper. This architecture pays off from n≥5 and where audit requirements exist. Better to say so before the finance function does.
- **Quality depends on source data.** If requirements in Polarion are untyped and naming in Teamcenter is inconsistent, the platform will make the mess more visible without fixing it. The project carries a data remediation component that must be budgeted separately.
- **Tools without TRS force polling, and polling misses deletions.** Periodic reconciliation is required, at a cost and with a window of inconsistency.
- **Spreadsheets are the worst case**: no identity, no events, no types. They must be treated as controlled artefacts in PLM and extracted at check-in. Left on a file share, they cannot be integrated reliably.
- **CAD is not genuinely integrable at the semantic level.** The file and its metadata can be linked; geometry and features cannot. Anyone promising otherwise is selling something else.
- **Lyo is not a product, and it is the only one there is.** It is a toolkit: it generates scaffolding, but semantic mapping, identity management and resilience remain development and maintenance work. And it has no competitors — the OSLC ecosystem offers no second complete SDK, in any language.

## Principal risks

| Risk | Likelihood | Impact | Mitigation |
| ------------------------ | ---------- | --------- | ----------------------------------- |
| No adoption: users stay on Excel | High | Critical | Integrate inside the tools, pilot with volunteers, demand-based exit criteria |
| Dependence on Eclipse Lyo with no real alternative | High | High | Isolate Lyo behind our own interfaces; no Lyo type crosses the adapter layer |
| A Lyo vulnerability not patched in time | Medium | Critical | CVE monitoring, SBOM per release, 30-day patch policy |
| Scarcity of OSLC skills on the market | High | High | Internal training, pair programming on the first adapter, documented patterns |
| Lyo upstream slows down or is abandoned | Low | High | The EPL licence permits a fork; keep build capability in house |
| Configuration mapping is wrong | Medium | Critical | Address in phase 0, validate against real variant cases |
| The graph becomes a second master | Medium | High | Full rebuild test at every release |
| A tool upgrade breaks an adapter | High | Medium | Automated contract tests, adapter versioning |
| Export control constraint missed | Low | Critical | Legal review in phase 0 |
| Triple store cost underestimated at scale | Medium | Medium | Realistic load test in phase 2 |

## Platform risk: the dependence on Eclipse Lyo

**Lyo is a technology constraint, not a choice among peers.** No second complete OSLC SDK exists: the alternatives are client libraries (oslc-client for Node.js), experimental middleware (oslc-service), or hand implementation on a Linked Data stack. For Python and .NET there is nothing at all. No alternative covers TRS, delegated UI and resource shapes together.

The platform therefore inherits the fate of a single open-source project, and a plan B does not exist ready to use — it has to be built. The spread of MCP reduces this risk: wherever the vendor exposes an MCP server, access no longer goes through Lyo, which remains necessary only for link semantics, delegated dialogs and tools that are already OSLC-native.

**The risk is manageable, but it must be managed explicitly.**

- **Isolation.** Adapters expose their own interfaces to the rest of the platform. No Lyo type and no OSLC4J annotation crosses the L1 boundary. If an adapter has to be rewritten without Lyo, nothing else notices.
- **Vulnerability management.** Lyo issues security releases: 6.0.1.Final, in August 2026, fixed an authentication bypass in two-legged OAuth flows (CVE-2026-18918). This needs a process — an SBOM per release, CVE monitoring, a declared patch window — not an occasional check. That the project issues CVEs and patches is itself a sign of active maintenance.
- **Minimum version.** No environment, not even a prototype, may run below 6.0.1.
- **Skills.** RDF, shapes and content negotiation are not found on the job market. The mitigation is internal: train two or three people on the first adapter and document the patterns while building, not afterwards.
- **Right to fork.** Lyo is under the EPL, so a fork is legally possible. For it to be practically possible, the ability to build and release the libraries — not merely consume them — must be kept in house.

**What not to do.** Do not write an abstraction layer so that "Lyo can later be swapped for another OSLC framework". The other framework does not exist, and the abstraction would cost today for an option that will never be exercised. Isolation exists so an adapter can be rewritten by hand, not to make Lyo interchangeable.

## Open questions to settle before deciding

1. **SysML v2 and its standard API** change the calculus on some MBSE adapters. If the tool estate migrates within two to three years, adapters built today will become redundant. Vendor roadmaps must be checked before sizing the investment.
2. **Triple store selection**: performance on deep path queries diverges widely between products. A benchmark against your own query patterns is required, not vendor marketing figures.
3. **The OSLC Configuration Management versioning model** is powerful but complex, and real vendor support is uneven. What is actually implemented must be verified tool by tool.
4. **Build versus buy**: Syndeis, SodiusWillert and commercial platforms cover part of this scope. The comparison must be made on five-year total cost, including maintenance of custom adapters.
5. **Ontology governance**: who decides that a new link type is permitted? Without an owner for the semantic model, the ontology diverges within six months.
6. **Internal cost model**: who pays for the platform when it serves five different projects? This organisational question sinks more projects than technical ones do.

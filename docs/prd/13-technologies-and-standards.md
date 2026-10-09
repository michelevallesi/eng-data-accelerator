# Technologies and standards

OSLC remains the right choice for the link and federation layer, but it should not be used alone: it is strong exactly where other standards do not reach, and weak exactly where better alternatives exist.

## Strengths

| Aspect | Why it matters |
| -------------------------- | ---------------------------------------------------- |
| Vendor-neutral OASIS standard | no lock-in, longevity independent of any supplier |
| Built on Linked Data | identity is already a URI and resources are already RDF: no mismatch with the graph |
| Designed to federate | no data migration and no central master required |
| Delegated UI | the only standard that solves integration at the interface level, not just the data level |
| TRS | standardised change propagation, rare among integration standards |
| Configuration Management | the only standard that addresses cross-tool variants and baselines |
| Real adoption | native support in Polarion, DOORS Next, Jama, codebeamer, IBM ELM |
| Shape-based extensibility | properties can be added without breaking existing consumers |

## Weaknesses

| Aspect | Practical impact |
| -------------------------- | ---------------------------------------------------- |
| Steep learning curve | RDF, shapes and content negotiation: few developers on the market |
| Narrow ecosystem | small community, few libraries, limited support |
| Uneven implementations | "supports OSLC" can mean a static catalogue and nothing more |
| Weak OSLC Query | no joins, aggregations or path traversal |
| Verbosity and performance | RDF/XML is heavy; bulk operations are not natively provided |
| Partial domain coverage | RM, QM, CM and AM exist; calculation, simulation, CAD and plant do not |
| Slow evolution | standard governance moves on long timescales |

## Technologies to pair with it

None of these replaces OSLC as the federated link layer. All of them cover a gap OSLC leaves open.

| Technology | Role | Where it replaces OSLC |
| ---------------------- | -------------------------- | ------------------------------ |
| JSON-LD | serialisation instead of RDF/XML | never: same semantics, far better ergonomics |
| GraphQL | read facade for user interfaces | on read queries, not on links or writes |
| SPARQL 1.1 | analytics and path traversal on the graph | replaces OSLC Query in the materialised plane |
| SHACL | verifiable resource contracts | complementary to OSLC Resource Shapes |
| CloudEvents + Kafka | standardised event transport | sits alongside TRS, avoiding pure polling |
| SysML v2 API | native access to MBSE models | a better source than a generic OSLC adapter |
| DEXPI / ISO 15926 | process plant semantics | covers a domain OSLC lacks |
| Asset Administration Shell (IEC 63278) | assets, production, Industry 4.0 | covers the asset and runtime side |
| OPC UA | operational automation data | a domain OSLC does not touch |
| ReqIF | batch requirement exchange | complementary: OSLC is online, ReqIF is contractual |
| OpenAPI | non-OSLC application endpoints | for platform APIs, not for links |

## Recommended pattern

OSLC as the integration contract and link layer. **JSON-LD** as the default serialisation. **GraphQL** as the read facade for consoles. **SPARQL** for analytics. **CloudEvents** for events. Domain standards — SysML v2 API, DEXPI, AAS, OPC UA — as semantic sources of truth in their own perimeters, with OSLC adapters exposing them to the link layer.

**Why not abandon OSLC.** The candidate alternatives — proprietary APIs, iPaaS platforms such as MuleSoft or Boomi, ETL pipelines — solve data transport but solve neither stable identity nor link semantics. The result is reinventing OSLC, worse and without a standard.

## Where each recommendation lands

Every technology recommended above is bound to specific requirements and use cases, so the architectural choice is verifiable rather than an intention.

| Recommendation | Requirements | Use cases |
| ---------------------- | -------------------------------------- | -------------- |
| JSON-LD as serialisation | FR-A11 | all (cross-cutting) |
| GraphQL as read facade | FR-M09, FR-M10, FR-C08, CR-19, UI-U08, UI-U09 | UC-13 |
| SPARQL for analytics | FR-M04, UI-U03 | UC-02, UC-03, UC-11 |
| SHACL as contract | FR-A08, FR-M06, CR-09, CR-16 | UC-07 |
| CloudEvents + Kafka | FR-A12, FR-B09, FR-B10, FR-B11, CR-20 | UC-08 |
| SysML v2 API as source | FR-A13, FR-A14, FR-M11, CR-18 | UC-14 |
| DEXPI / ISO 15926 | FR-A13, FR-M11, CR-18 | UC-15 |
| AAS and OPC UA | FR-A13, FR-M11, CR-18 | UC-16 |
| ReqIF for batch exchange | FR-C03 | UC-10 |
| OpenAPI for platform APIs | FR-C06 | UC-13 |

The three highest-priority items are JSON-LD, GraphQL and CloudEvents: they affect every adapter and every consumer, so they belong in phase 0 alongside the identity strategy. The domain sources (SysML v2, DEXPI, AAS, OPC UA) are per-adapter decisions and can wait until that tool enters scope.

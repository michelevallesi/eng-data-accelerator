# Functional requirements

MoSCoW priorities. Every requirement is verifiable: if there is no way to test it, it is badly written.

## Adapter layer (FR-A)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-A01 | Expose native resources as OSLC resources conformant to Core 3.0 | Must |
| FR-A02 | Publish a navigable Service Provider Catalog | Must |
| FR-A03 | Expose a TRS change log with base and delta | Must |
| FR-A04 | Provide selection and creation delegated dialogs | Must |
| FR-A05 | Provide compact rendering for hover previews | Should |
| FR-A06 | Support OSLC query with property filters | Must |
| FR-A07 | Propagate user identity to the source system; never a service account for application reads | Must |
| FR-A08 | Validate published resources against SHACL before emission | Should |
| FR-A09 | Declare deletions explicitly in the TRS | Must |
| FR-A10 | Degrade explicitly when the source system is unavailable, never emitting silent partial data | Must |
| FR-A11 | Serialise in **JSON-LD** by default, with RDF/XML and Turtle available through content negotiation | Must |
| FR-A12 | Emit changes as **CloudEvents** as well as through the TRS | Should |
| FR-A13 | Where a domain standard API exists (**SysML v2 API**, **DEXPI/Proteus**, **AAS**, **OPC UA**), use it as the source and expose OSLC as a facade, rather than mapping from proprietary APIs | Should |
| FR-A14 | Declare in the catalogue which domain standard feeds the adapter, and at which version | Should |

## Integration bus (FR-B)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-B01 | Mediate SOAP, REST, COM and file protocols towards OSLC | Must |
| FR-B02 | OAuth2/OIDC token brokerage with identity delegation | Must |
| FR-B03 | Configurable circuit breakers and throttling per endpoint | Must |
| FR-B04 | Idempotency and retry with exponential backoff | Must |
| FR-B05 | Inspectable and reprocessable dead letter queue | Must |
| FR-B06 | Event ordering per resource | Should |
| FR-B07 | Immutable audit log of every cross-tool call | Must |
| FR-B08 | Periodic reconciliation to catch deletions missed by polling | Must |
| FR-B09 | Carry all events as **CloudEvents** over Kafka, with mandatory provenance attributes | Must |
| FR-B10 | Map TRS change logs into CloudEvents, so polling stays an adapter detail and never a concern of the bus | Must |
| FR-B11 | Publish the event contract as a versioned **AsyncAPI** specification, with a schema registry and compatibility rules: in an event-driven architecture the schema is the API | Must |

## Federated plane (FR-F)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-F01 | Maintain a Link Index holding link metadata only | Must |
| FR-F02 | Resolve a URI to its native artefact | Must |
| FR-F03 | Support OSLC Configuration Management: streams, baselines, global configurations | Must |
| FR-F04 | Guarantee logical bidirectionality even where a tool stores links on one side only | Must |
| FR-F05 | Allow link creation from either side | Must |
| FR-F06 | Handle links to deleted artefacts by marking them dangling, not deleting them | Must |

## Materialised plane (FR-M)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-M01 | Ingest TRS events and CloudEvents and project them into RDF triples | Must |
| FR-M02 | Record PROV-O provenance on every assertion | Must |
| FR-M03 | Allow a full rebuild of the graph from scratch | Must |
| FR-M04 | Expose a SPARQL 1.1 endpoint with access control, for analytics | Must |
| FR-M05 | Retain version history with temporal queries | Should |
| FR-M06 | Validate the graph against SHACL shapes and report violations | Should |
| FR-M07 | Index heavy payloads in the lakehouse under the same URI | Should |
| FR-M08 | Expose data age for every projected node | Must |
| FR-M09 | Expose a read-only **GraphQL** facade over the graph, with a schema generated from the SHACL shapes and regenerated at every release | Should |
| FR-M10 | Enforce access control in the GraphQL facade, failing explicitly on unauthorised fields rather than returning partial results | Must |
| FR-M11 | Ingest external domain models (**DEXPI/Proteus**, **SysML v2**, **AAS** submodels) mapped onto the ontology without losing native identifiers | Should |

## Consumption (FR-C)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-C01 | Generate parametric traceability matrices | Must |
| FR-C02 | Run impact analysis configurable by depth and relationship type | Must |
| FR-C03 | Export to Excel, PDF and **ReqIF** | Should |
| FR-C04 | Coverage and link-quality dashboards | Should |
| FR-C05 | Compliance reports referencing a baseline | Should |
| FR-C06 | Platform APIs documented as **OpenAPI** for non-OSLC operations | Should |
| FR-C07 | MCP server over the graph and the Terminology Service as the primary interface for agents: read-only at first; writes to the *proposed* lane only after semantic and AI governance approval (FR-AG06) | Must |
| FR-C08 | Consoles and third-party applications consume the graph over GraphQL; direct SPARQL is reserved for analytics and expert users | Should |

## Data governance (FR-G)

| ID | Requirement | Priority |
| ---------- | -------------------------------------------------------- | ----------- |
| FR-G01 | Recognise the PLM backbone as the product and configuration anchor: every product-relevant link carries the applicable revision and baseline | Must |
| FR-G02 | Record, for each resource type, the authority over object, attribute, relationship, lifecycle and baseline | Must |
| FR-G03 | Admit a resource type into the hub only if it passes the nine-question authority test | Must |
| FR-G04 | Support intentional publication with the minimum envelope as a SHACL shape; reject incomplete packages with a reason code and a resubmission path | Must |
| FR-G05 | Record every promotion decision with one of four outcomes: remain federated, revise, reject, promote | Must |
| FR-G06 | Execute promotion only through pre-approved backbone representation patterns, with idempotent transactions and identifiers returned to the hub | Must |
| FR-G07 | Never overwrite released evidence: supersession creates a new controlled revision | Must |
| FR-G08 | Hook impact analysis, notifications and triage to the backbone's Change lifecycle; report direct edits to released objects as anomalies | Must |
| FR-G09 | Declare, for each information type, the custody pattern with owners for retention, deletion, recovery and integrity | Must |
| FR-G10 | Pin events to immutable version URIs and retain the log for the full programme retention period, or equivalent snapshots | Must |
| FR-G11 | Expose publication status and promotion status as maturity axes distinct from lifecycle | Must |
| FR-G12 | Compute the programme KPIs in the cockpit, with formula, source, frequency and owner | Should |
| FR-G13 | Score every onboarding candidate with a weighted scorecard; the access pattern (MCP, OSLC, API, file) feeds the interface-feasibility criterion | Should |
| FR-G14 | Support as-designed, as-built and as-flown views, NCRs and declared lists as hub resource types | Should |
| FR-G15 | Ingest early-phase concurrent design parameters without manual recreation, normalising quantities through the Terminology Service | Should |
| FR-G16 | Maintain traceability between this document's requirements and the layers and decisions of the programme functional architecture | Should |
| FR-G17 | Apply the propagation regime according to configuration-control status: notification and disposition before release (FR-S15), only through a Change after release (FR-G08) | Must |
| FR-G18 | Provide a publication kit for desktop tools — CLI and libraries for Python, MATLAB and Excel — that reads inputs from the platform with their version, composes the envelope and publishes | Should |
| FR-G19 | Record in the lineage of every publication the version of the inputs read from the platform; reject manually declared inputs that do not resolve to a registered version | Must |
| FR-G20 | Accept spreadsheet publications only when based on a controlled template, with defined names for inputs and outputs and declared units; reject free-form sheets with a reason code | Must |
| FR-G21 | Require the model and parameters of every publication to be identified by an immutable reference in a versioned repository | Must |
| FR-G22 | Support FMI and SSP as exchange formats for simulation models and parameters | Could |

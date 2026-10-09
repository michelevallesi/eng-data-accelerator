# Glossary

| Term | Meaning |
| ---------------------- | -------------------------------------------------------- |
| OSLC | Open Services for Lifecycle Collaboration: OASIS standard for integrating lifecycle tools through RDF resources over HTTP |
| Eclipse Lyo | Open-source Java toolkit for building OSLC clients and servers |
| OSLC4J | Lyo library for exposing Java objects as OSLC resources |
| Lyo Designer | Generator of adapter scaffolding from domain models |
| TRS | Tracked Resource Set: OSLC incremental change log mechanism, base plus delta |
| Delegated dialog | A window supplied by the remote tool and opened inside the local one to select or create a resource |
| Compact rendering | A concise representation of a remote resource, for previews |
| OSLC CM (Configuration Management) | OSLC domain defining streams, baselines and global configurations |
| Global configuration | A coherent set of configurations across tools, defining the validity context of a link |
| SHACL | Shapes Constraint Language: W3C language for defining and validating constraints on RDF graphs |
| PROV-O | W3C ontology for representing data provenance |
| SPARQL | Query language for RDF graphs |
| ESB | Enterprise Service Bus: protocol mediation, routing and security layer |
| Data federation | Accessing data in the source system at query time, without copying it |
| Data fabrication | Materialising a projection of the data in a dedicated store, for queries federation cannot serve |
| Link Index | An index of link metadata only, without artefact content |
| Tombstone | A record preserving the URI of a deleted artefact, so it is never reassigned and links are not lost |
| Lakehouse | A store combining object storage and transactional tables, for heavy payloads and tabular data |
| ReqIF | OMG standard format for requirement exchange |
| MCP | Model Context Protocol: a protocol for exposing tools and data to AI assistants |
| System of Record | The authoritative platform for an object, attribute, relationship, lifecycle state or baseline |
| Publication | Intentional sharing of a versioned representation for authorised cross-domain use |
| Promotion | Controlled creation or update of a governed Teamcenter representation from a reviewed publication |
| Publication envelope | The minimum field set accompanying a publication: identity, context, configuration, content, provenance, maturity, security, quality, lineage |
| Governed reference | Controlled identity and stable link to an external authoritative object |
| Snapshot | Version-specific frozen representation retained for decision traceability |
| Derived assertion | A calculated conclusion or relationship kept with its sources and validation outcome; it does not replace source facts |
| Custody pattern | How the Data Hub holds an information type: index, cache, preserved package, external repository, promoted representation |
| Coordinated baseline | An approved set of references to domain baselines, governed in Teamcenter's product context |
| PLM backbone | The platform that governs product, configuration, Change and release in a programme |
| Concurrent design | Early-phase design environment where mission and system parameters are set in joint sessions |
| Accelerator | Reusable set of software components, reference models and method that a delivery team instantiates at a programme |
| Integration bus | Layer L2: mediation ESB for protocols and security, event bus for change propagation |
| Engineering data hub | The federated and materialised planes together: catalogue, lineage, provenance and cross-cutting views, with no lifecycle authority |
| Disposition | The owner's recorded decision on a superseded item: confirmed on the new version, updated, to be rerun, not applicable |
| Superseded input | Condition of a published result when an input declared in its lineage has a new version: the result stays accessible but out of the evidence |
| Publication kit | CLI and libraries for Python, MATLAB and Excel with which desktop tools read inputs from the platform and publish results with their envelope |
| Controlled template | Spreadsheet with defined names for inputs and outputs, declared units and an envelope sheet: the only form in which an Excel file can be published |

# Application requirements — Configuration

Configuration is declarative and versioned as code. Every change follows a propose, validate in sandbox, approve, promote flow.

## Tool registration (CR-01…05)

- **CR-01** — Register a tool with endpoint, adapter type, credentials and environment (dev, test, prod).
- **CR-02** — Test the connection with readable diagnostics: reachability, authentication, catalogue, TRS.
- **CR-03** — Define the synchronisation pattern: TRS push, TRS polling with interval, or scheduled batch.
- **CR-04** — Declare the exposed resource types and published fields, with explicit opt-in field by field.
- **CR-05** — Define the retention policy for data projected from that tool.

## Semantic mapping (CR-06…10)

- **CR-06** — Mapping editor between native types and OSLC classes, with a preview on a real sample.
- **CR-07** — Map attributes with declarative transformations: units, date formats, enumerations, concatenations.
- **CR-08** — Define relationships and their semantic direction, with expected cardinality.
- **CR-09** — Validate the mapping against SHACL shapes before promotion.
- **CR-10** — Version mappings and allow rollback to a previous version.

## Identity and resolution (CR-11…13)

- **CR-11** — Configure the URI minting strategy per tool, with a dedicated namespace.
- **CR-12** — Configure URI to native deep-link resolution rules, per resource type.
- **CR-13** — Define reconciliation rules when the same artefact appears in several tools.

## Vocabularies and rules (CR-14…17)

- **CR-14** — Import and manage vocabularies and ontologies, with versioning.
- **CR-15** — Define which link types are permitted between pairs of resource types.
- **CR-16** — Define completeness and quality rules as SHACL shapes, with severity levels.
- **CR-17** — Configure global configurations and their mapping to native tool contexts.
- **CR-18** — Declare a tool's binding to a domain standard (SysML v2 API, DEXPI, AAS, OPC UA) and the mapping rules towards the ontology and towards OSLC.
- **CR-19** — Regenerate and publish the GraphQL schema from the shapes, with a diff against the previous version and flagging of breaking changes.
- **CR-20** — Configure the TRS to CloudEvents mapping: topic, partition key, provenance attributes.
- **CR-21** — Define and approve native-term to canonical-concept mappings, with mandatory four-eyes approval for units and quantities.
- **CR-22** — Configure which AI models are permitted, for which data class, and within which hosting perimeter.
- **CR-23** — Configure the confidence thresholds above which an AI suggestion is offered and below which it is discarded.
- **CR-24** — Define notification routing rules by significance class.
- **CR-25** — Define recipient resolution rules per tool, model or package, with a fallback owner when the artefact declares none.
- **CR-25** — Define the canonical maturity scale, its ordering, and the maturity constraints permitted for each link type.
- **CR-26** — Configure cockpit panels, traffic-light thresholds with their named owner, and the landing layout per role.

**Critical point.** CR-17 is the requirement that sinks projects. Different tools hold different notions of variant, revision and baseline. If the mapping between contexts is not explicit and verified, links will look correct and be semantically false. It must be addressed before the first adapter, not after.

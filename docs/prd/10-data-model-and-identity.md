# Data model and identity

## URI strategy

URIs are opaque, stable and resolvable. The form is `https://<domain>/r/<tool-namespace>/<opaque-identifier>`. The identifier never contains a path, file name, revision number or any attribute a user can change.

A **Resolver** service maintains the mapping between URI and native identity, and is the only component allowed to know it. This makes it possible to change a tool's native key — which happens during migrations — without invalidating existing links.

| Case | Strategy |
| ------------------------------ | ------------------------------------------------ |
| Artefact renamed | URI unchanged, the name is an attribute |
| Artefact revised | version URI plus concept URI, both resolvable |
| Artefact migrated to another tool | original URI retained, aliased to the new one |
| Artefact deleted | URI kept as a tombstone, never reassigned |
| File with no native identity | synthetic URI anchored to the PLM check-in, never to the path |

## Semantic layer

Three stacked levels, from general to specific:

1. **OSLC core and domains** (RM, QM, CM, AM) — the base vocabulary, interoperable by construction.
2. **Enterprise domain ontology** — the concepts OSLC does not cover: calculation case, simulation configuration, production tooling, plant equipment.
3. **Alignments to external standards** — ISO 15926 and CFIHOS on the plant side, the PLM data model on the product side, ReqIF for requirement exchange.

Every resource type has a **SHACL shape** defining its contract: mandatory properties, cardinalities, permitted types. The shape is the single verification point, used both by the adapter on the way out and by the graph on the way in.

## Provenance

Through PROV-O, every assertion in the graph carries the adapter that generated it, the originating TRS event, the generation timestamp, the modification timestamp in the source system, and the mapping version used. Without the last item it is impossible to explain why an old projection differs from a new one.

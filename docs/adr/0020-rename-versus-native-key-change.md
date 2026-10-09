# ADR-0020: Rename is an attribute change; a native-key change is an alias event

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

S02 states that renaming `400-1105/C` keeps its URI. This is the right outcome
for a human-readable name change, but Teamcenter and PLM migrations also mutate
the *native key* itself (part number, item id). The PRD data-model table
distinguishes "Artefact renamed" (URI unchanged) from "Artefact migrated to
another tool" (original URI retained, aliased to the new one), but does not say
which category a bare native-key change falls into. Without an explicit decision,
S02 and S07 can disagree on whether the URI survives.

## Decision

- **Rename event.** Change of a human-readable attribute (name, title, label).
  No URI change, no alias. The attribute change arrives through
  `eda.resource.changed` with the new value in the payload.
- **Native-key change.** Change of the identifier that the source system uses as
  its primary key. The Resolver emits `eda.uri.aliased` from the old concept URI
  to the new one; both remain resolvable. The Link Index does not touch the
  links — they continue to pin the concept URI and the alias machinery takes
  care of resolution. Dependent material (named graphs, link-state records) is
  not rewritten.
- **Delete.** `eda.uri.tombstoned`; URI is never reassigned (AD-03).

## Alternatives considered

- Treat rename and native-key change as the same operation (URI unchanged for
  both): breaks link stability when a tool treats the native key as the deep-link
  anchor and loses the ability to detect that a key has moved.
- Mint a new URI on every native-key change with no alias: breaks AD-03 and
  silently invalidates every existing asserted link.

## Consequences

- Adapters classify incoming changes into *attribute* vs *key* changes before
  calling the Resolver. For tools where the native key is immutable by policy
  (Polarion requirement ids), key changes never occur and this branch is dead
  code.
- Tests for S02 cover an attribute change (URI stable) and a native-key change
  (one `eda.uri.aliased` emitted, both URIs resolve to the current deep link).

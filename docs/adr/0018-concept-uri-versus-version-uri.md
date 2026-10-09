# ADR-0018: Concept URI and version URI are both minted and both resolvable

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

PRD chapter 10 ("Data model and identity") states that a revised artefact has a
"version URI plus concept URI, both resolvable". S05 requires a new version URI
on each Teamcenter revision. FR-S13 requires asserted links to pin the version of
each endpoint at the time of assertion. The exact relationship between the two
URIs is not currently fixed by any ADR, which leaves S02, S05 and S07 free to
diverge.

## Decision

- A **concept URI** names an artefact by identity and is minted once. It is
  rename-stable (ADR-0003) and alias-tracked (FR-F02).
  Form: `https://<base>/r/<toolNs>/<UUIDv4>`.
- A **version URI** names a specific revision of that artefact. It is minted at
  every revision event.
  Form: `https://<base>/r/<toolNs>/<UUIDv4>/v/<versionId>` where `versionId` is the
  source system's version token (opaque, treated as a string).
- `asserted` and `validated` links pin the version URI of each endpoint, per
  FR-S13. `proposed` links reference the concept URI.
- The Resolver resolves both: the concept URI redirects to the current native
  deep link; the version URI redirects to the versioned native representation
  when the source system exposes one, otherwise to the current representation
  with a `Link: rel="latest-version"` header.

## Alternatives considered

- Single URI with a `?version=` query parameter: unfriendly to SPARQL named
  graphs and to RDF triple identity.
- Separate opaque UUID per revision with no derivable relationship: loses the
  "both resolvable" property PRD requires and makes rebuild harder.
- Store the revision only as a property on the concept URI's resource: breaks
  FR-S13 pinning because the property changes over time.

## Consequences

- The Resolver stores two row kinds: concept URIs (unique on `(tool, nativeId)`)
  and version URIs (unique on `(tool, nativeId, versionId)`).
- The projection service writes one named graph per version URI and keeps a
  concept-level named graph that carries `eda:current` pointing to the current
  version graph and `prov:revisionOf` links between versions.
- S07's state machine, on seeing a `eda.resource.changed` whose subject is a new
  version URI, transitions links whose source or target was the previous version
  to `superseded` (FR-S08) and pins the new version in the superseded record.

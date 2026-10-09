# ADR-0019: The URI base domain is per-programme configuration

- **Status:** Proposed
- **Date:** 2026-10-01

## Context

PRD chapter 10 writes URIs as `https://<domain>/r/<tool-namespace>/<opaque-identifier>`
and leaves `<domain>` parameterised. The fixtures hard-code
`https://eda.example.org`. Without an explicit decision, the Resolver may bake a
domain into code or the installer may need to rewrite URIs on every deployment,
either of which breaks AD-03 (opaque, stable URIs).

## Decision

- The base domain is a programme-level setting read from configuration
  (`eda.base.domain`), consumed by the Resolver and by every service that mints
  or renders URIs.
- The setting is fixed at install time and never changed in place. Changing it
  requires an alias migration: every live concept URI is aliased
  (`eda.uri.aliased`) to a new URI under the new domain, both remain resolvable.
- Default in fixtures, dev stack and tests: `eda.example.org`.

## Alternatives considered

- A single global domain owned by Anthropic/Siemens/the integrator: conflicts
  with DR-03 isolated installations and with multi-tenant deployments where
  each customer owns its own URI namespace.
- Encode the domain into each URI at mint time with no central setting: makes
  the mint path depend on request headers (`Host`) and loses determinism in
  tests.

## Consequences

- A test that mints and resolves a URI must set `eda.base.domain`; the
  `shared/eda-test-support` module exposes a default of `eda.example.org` for
  determinism.
- The Resolver rejects attempts to resolve a URI whose host does not match the
  configured base domain, except for aliased rows pointing to the new domain.

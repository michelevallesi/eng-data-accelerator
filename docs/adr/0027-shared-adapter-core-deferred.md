# ADR-0027: Shared adapter-core and shared simulator-core extraction deferred

- **Status:** Proposed
- **Date:** 2026-10-07

## Context

After S05, the Polarion and Teamcenter adapters are ~90% identical:

- `TeamcenterClient` vs `PolarionClient`: thin `RestClient` wrapper, same error
  handling, same availability bookkeeping.
- `TrsPoller`, `KafkaRelay`, `OutboxRepository`, `CursorRepository`,
  `ProcessedEventsRepository`, `CloudEventFactory`, `KafkaPublisher`,
  `DlqPublisher`, `ShaclValidator`, `AdminController` — pairwise copies.
- Test harness: `AbstractAdapterIntegrationTest`, `SimulatorHarness`, the
  mock-maker-subclass extension — pairwise copies.

The two simulators also duplicate ~90%: `OslcVocabulary`, `OslcModels`,
`ModelHttpMessageConverter`, `WebConfig`, `ClockConfig`,
`IdentityPropagationFilter`, `WhoAmIController`, catalog/TRS/URI-resource
controllers.

## Decision

**Defer extraction** of `shared/eda-adapter-core` and `shared/eda-simulator-core`
until a third adapter or a third simulator is implemented. The walking skeleton
tolerates the duplication because:

1. The pattern is **not yet stable**. The outbox + relay model stabilised in the
   S04 review; the simulator's URI-dereference endpoint arrived in the same pass.
   Freezing an API now would almost certainly need breaking changes in S06-S08.
2. The duplication is **mechanical, not semantic**. A diff between the two
   adapters is trivially reviewable and the risk of divergence is caught by the
   mirrored tests on both sides.
3. The extraction cost is non-trivial: parameterising the generic types
   (`PolarionTrsEvent` vs `TeamcenterTrsEvent`, `PolarionResource` vs
   `TeamcenterResource`), the SHACL shape path, the DB table prefix, the source
   URN, the topic names, the Spring configuration keys — all of these need
   generic plumbing before a shared module adds value.

## Trigger

Extract when any **one** of these becomes true:

- A third adapter (Capella, COMOS, Ansys, …) begins.
- A behaviour change needs to land in both existing adapters simultaneously
  (e.g. retry policy, metrics emission).
- A production hardening pass needs to replace the blocking `RestClient` with
  async IO in both adapters (deferred by ADR-0025).

## Alternatives considered

- **Extract now.** Rejected: the API would freeze before the pattern is stable.
- **Hand-roll a trait/interface layer in Java.** Possible but adds plumbing for
  no immediate gain while two consumers exist.
- **Code generation (OpenRewrite recipes, Lombok delegates).** Over-engineered
  for the walking-skeleton size.

## Consequences

- A bug fixed in the Polarion adapter must be mirrored manually to Teamcenter
  until extraction. The S04/S05 review passes already caught two such drifts;
  the next review pass should keep doing that.
- The parent POM's `<classifier>exec</classifier>` default is a shared
  convention that both adapters already honour; new adapters inherit it.
- When extraction happens, this ADR is marked `Superseded by ADR-NNNN` and the
  new module becomes the single source of truth.

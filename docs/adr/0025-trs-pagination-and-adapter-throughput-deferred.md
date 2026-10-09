# ADR-0025: TRS pagination and adapter throughput deferred to a hardening story

- **Status:** Proposed
- **Date:** 2026-10-02

## Context

Two closely-related shortcuts live in the S03 Polarion simulator and the S04
Polarion adapter:

1. **No TRS pagination.** The simulator's `GET /trs/changes?since=<order>` returns
   the whole delta on a single page. The adapter calls it and expects the whole
   list to come back on one page. OSLC TRS 3.0 defines `oslc_trs:previousPage` /
   cutpoint IRIs for walking a large change log one page at a time; neither side
   implements that.
2. **Blocking HTTP + single-threaded poller.** `PolarionClient` uses Spring's
   `RestClient` and `KafkaPublisher`/`DlqPublisher` block on `KafkaTemplate.send().get()`.
   The scheduled poller is single-threaded.

Both are intentional for the walking skeleton: the SKID-400 and AERO-7 fixtures
have < 50 artefacts between them, so a one-page change log and a single-threaded
loop are not the bottleneck. The risk is real at programme size (PRD aggregate
figure: ~1,200 artefacts per programme, with change bursts).

## Decision

Pagination and throughput improvements are **explicitly deferred** to a dedicated
hardening story after the walking skeleton ships, under these guarantees:

- The adapter's **correctness properties** (ADR-0016 outbox + deterministic
  CloudEvent ids, FR-A10 explicit degradation, FR-B06 per-URI ordering) do not
  depend on pagination or concurrency. They continue to hold at any throughput.
- The adapter remains **safe to run against a paginated TRS** once the simulator
  grows pagination: a single-page response is a degenerate case of a paginated
  log, so the adapter's loop over `fetchChangesSince` already handles "the
  whole delta came back on one call".
- The outbox (ADR-0016) decouples ingestion speed from publish speed. The relay
  can be scaled independently when it matters.

The hardening story must cover:

- Simulator: `oslc_trs:previousPage`, cutpoint IRIs, page-size cap.
- Adapter: paginated pull, bounded parallelism per source, backpressure through
  the outbox (bounded `loadPending` batch sizes already exposed in config).
- A throughput benchmark on a representative scenario (ADR-0015 refers to a
  ~1,200-artefact synthetic programme).

## Alternatives considered

- **Pagination now.** Would push S03/S04 past the "walking skeleton" bar for no
  immediate test gain. The fixture-sized reactor completes in a single page.
- **Non-blocking IO now (WebClient, async Kafka producer).** The outbox pattern
  already breaks the ingestion/publish coupling. Non-blocking IO is a throughput
  optimisation worth measuring, not a correctness fix.

## Consequences

- No code change in S03 or S04 beyond what already shipped.
- A production-readiness checklist in the hardening story will reference this
  ADR.
- If a programme between the walking skeleton and the hardening story needs
  real-size runs, the operator-facing docs must call out the known limits.

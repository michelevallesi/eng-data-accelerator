# ADR-0017: Desktop tools integrate through intentional publication, with one publication kit

- **Status:** Proposed
- **Date:** 2026-10-02

## Context

MATLAB, Simulink, OpenModelica and calculation spreadsheets have no server to query and no
change log. Their results still matter: they are verification evidence and inputs to
decisions. The PRD requires a publication kit (FR-G18), pinned input versions (FR-G19),
controlled spreadsheet templates (FR-G20), immutable model references (FR-G21) and, later,
FMI/SSP exchange (FR-G22).

## Decision

1. Desktop tools never get a live adapter. They integrate only through intentional
   publication (AD-07).
2. One publication kit: a command-line tool `eda` written in **Python**, with thin
   bindings — MATLAB functions that call it, and an Excel path through controlled templates
   that the CLI reads and writes. An Office add-in is a later option.
3. Inputs are pulled from the platform by the kit, with their version. Inputs typed by hand
   are never accepted as lineage (FR-G19).
4. Model and parameters are cited by an immutable reference (git commit, PDM revision),
   filled in by the kit from the working copy. A dirty working copy blocks publication (FR-G21).
5. Spreadsheets are publishable only from registered controlled templates using the defined
   names `EDA_IN_*`, `EDA_PAR_*`, `EDA_OUT_*`, `EDA_ENV_*` (FR-G20).
6. The envelope contract is `docs/specs/publication-envelope.shacl.ttl`; reason codes are
   in `docs/specs/publication-reason-codes.md`. Both change only through an ADR.

## Alternatives considered

- **One adapter per desktop tool:** cost grows with each tool, and there is still no change
  log to follow.
- **Watched folders only:** no input pinning and weak identity; kept as a later convenience.
- **Office add-in first:** heavier to build and deploy; deferred.
- **Kit written in MATLAB or Java:** MATLAB needs a licence in CI; Java is heavier to
  distribute to engineers' desktops. Python matches OMPython and common engineering setups.

## Consequences

- A second language enters the repository, on the client side only; services stay Java
  (ADR-0015).
- MATLAB bindings cannot run in CI without a licence: they are covered by contract tests on
  the REST API plus a scripted manual check.
- Controlled templates are versioned artefacts with an owner and an approval step.

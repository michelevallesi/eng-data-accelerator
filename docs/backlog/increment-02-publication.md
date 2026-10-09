# Increment 2 — Publication and desktop tools

## Goal

A result produced on a desktop enters the platform with pinned inputs, and becomes
"superseded input" as soon as one of those inputs changes.

**Demo that closes the increment:** CALC-HYD-03 is published from the controlled Excel
template. In the Polarion simulator HYD-REQ-041 changes from 118 kg to 115 kg. CALC-HYD-03
becomes *superseded input*, a disposition opens for its owner, and the cockpit shows it out
of the evidence. Pulling inputs again and republishing gives a margin of 6.5 kg.

**Prerequisite:** increment 1 done, including retrofit stories R01–R03.

## Scope

**In:** publication API, lineage pinning, superseded input and evidence exclusion, publication
kit CLI, Excel controlled templates, MATLAB/Simulink binding, OpenModelica example, cockpit
view of publications. **Out:** Office add-in, watched folders, FMI/SSP import (FR-G22),
promotion to the PLM backbone (FR-G06), MCP-based publication.

## Stories

### P01 — Publication API (FR-G04)
`POST /publications` with envelope JSON and files. Map the envelope to RDF, validate with
`docs/specs/publication-envelope.shacl.ttl`, return reason codes from
`docs/specs/publication-reason-codes.md` with HTTP 422. On success: URI from the Resolver,
`eda.publication.accepted` event, projection with PROV-O.
- AC: every file in `fixtures/publication/envelopes/valid/` is accepted.
- AC: every file in `envelopes/invalid/` is rejected with the code in `expected.json`.
- AC: a corrected resubmission is linked to the rejected attempt.

### P02 — Lineage pinning (FR-G19, FR-S13)
Every lineage input must resolve to a registered version; stored as `prov:used` with version.
- AC: `manual-input.json` is rejected with INPUT_UNRESOLVED.
- AC: the lineage of SIM-HYD-02 lists HYD-REQ-027 version 2 and LF-29-04 version 4.

### P03 — Superseded input and evidence exclusion (FR-S14, FR-S15, FR-S16)
- AC: a new version of any lineage input marks the result `superseded input` and opens a
  disposition for its owner (CR-25); the result stays readable.
- AC: evidence and compliance queries exclude it and state how many items were excluded.

### P04 — Publication kit CLI (FR-G18, FR-G21)
Python package with `eda inputs pull`, `eda publish`, `eda status`; project config file;
token-based auth seam; SHA-256 of published files.
- AC: publishing from a dirty git working copy is refused locally with MODEL_REF_REQUIRED.
- AC: `eda inputs pull` records URI, version and read time for every input.

### P05 — Controlled Excel templates (FR-G20)
The CLI reads and writes defined names; templates are registered (id + version) in
programme configuration.
- AC: `eda inputs pull` fills the `EDA_IN_*` cells of `mass-budget-template.xlsx` from the
  fixtures and records versions; `eda publish` reads `EDA_OUT_*` and `EDA_ENV_*`.
- AC: a sheet without the registered template is refused locally and by the API.

### P06 — MATLAB and Simulink binding
MATLAB functions `eda.inputs.pull` and `eda.publish` wrapping the CLI; for Simulink the
parameter reference is the data dictionary file in the repository (SIM-HYD-02).
- AC: REST contract tests run in CI without MATLAB; a scripted manual check is documented.

### P07 — OpenModelica example
Example project using OMPython and the Python library (SIM-SKID-05 on SKID-400).
- AC: the example publishes an envelope equal to `sim-skid-05-openmodelica.json` apart from
  generated fields.

### P08 — Cockpit: publications
List of published results with lineage, superseded-input badge and open dispositions.

### P09 — End-to-end (`make e2e-publication`)
Automates the demo above.

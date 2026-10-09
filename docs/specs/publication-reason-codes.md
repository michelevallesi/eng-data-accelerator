# Publication reason codes

Returned by the publication API with HTTP 422 and a resubmission path (FR-G04).

| Code | Meaning | Requirement | How to fix |
| --- | --- | --- | --- |
| IDENTITY_MISSING | result id or revision missing | FR-G04 | set `EDA_ENV_RESULT_ID` or `identity` |
| CONTEXT_MISSING | programme or tool missing | FR-G04 | run `eda publish` from a configured project |
| CONFIGURATION_MISSING | no configuration context | FR-G04 | declare the configuration (e.g. GC-AERO7-WIP) |
| MODEL_REF_REQUIRED | model or parameters not cited immutably | FR-G21 | commit the working copy, publish again |
| PROVENANCE_MISSING | author or time missing | FR-G04 | authenticate the kit |
| MATURITY_INVALID | publication status other than published | FR-G04 | remove manual status overrides |
| SECURITY_MISSING | classification missing | FR-G04 | set classification and export control |
| OUTPUT_MISSING | no output, or output without name or value | FR-G04 | fill `EDA_OUT_*` or `outputs` |
| UNIT_MISSING | an output has no unit | FR-T04 | declare the unit |
| LINEAGE_MISSING | no input declared | FR-G19 | run `eda inputs pull` |
| INPUT_UNRESOLVED | an input is not a registered version | FR-G19 | read the input from the platform, never type it |
| TEMPLATE_REQUIRED | spreadsheet not based on a registered template | FR-G20 | start from a controlled template |

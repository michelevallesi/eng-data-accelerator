# Fixtures

Two reference scenarios from the PRD annexes, used by simulators and tests. No real system
is ever contacted.

- `skid400/` — process skid (Annex A): Polarion, Capella, COMOS, Teamcenter, Ansys.
- `aero7/` — hydraulic system of a regional aircraft (Annex D): Polarion, Capella,
  Teamcenter, Ansys, CSDB S1000D.

Each folder: `tools.json`, `artefacts.json` (with pre-minted opaque URIs), `links.json`
(with validation state), `configurations.json`, `uris.json` (Resolver seed),
`maturity-scale.json` and `owners.json` (programme configuration: maturity scale and
notification recipients), and a Turtle rendering of the same data.
Links appear twice in Turtle: as a direct triple for navigation and as an `eda:Link`
resource carrying the validation state.

`skid400/expected/` holds expected results computed from the fixture itself. The PRD's
aggregate figures (e.g. 14 artefacts in UC-02) refer to the full project of about 1,200
artefacts, not to this subset.

Deviation from the PRD figure: the scenario map shows E-403 → 400-1180/A as dangling;
here it is `proposed`, because 400-1180/A exists. The dangling case is produced in tests
by deleting TC-211 through the Polarion simulator (story S03).

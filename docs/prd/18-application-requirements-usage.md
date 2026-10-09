# Application requirements — Usage

The design criterion is that engineers should not have to learn a new application. Seventy per cent of daily use happens inside authoring tools, through delegated dialogs and embedded views.

## Inside the authoring tool (UR-01…06)

- **UR-01** — Create a link from a selected artefact, choosing the target via a delegated dialog.
- **UR-02** — See incoming and outgoing links of an artefact in a side panel.
- **UR-03** — Compact preview of the target without opening the remote tool.
- **UR-04** — Open the target in its native tool with one click.
- **UR-05** — Remove a link, with confirmation and author recording.
- **UR-06** — Visual flagging of dangling links or configuration conflicts.

## In the usage console (UR-07…14)

- **UR-07** — Federated search across all tools, filtered by type, tool and configuration.
- **UR-08** — Graph exploration with incremental navigation from a starting artefact.
- **UR-09** — Impact analysis with a choice of direction, depth and relationship types to traverse.
- **UR-10** — Traceability matrix builder with axis and configuration selection.
- **UR-11** — Save, share and schedule queries and views.
- **UR-12** — Export to Excel, PDF and ReqIF with generation metadata.
- **UR-13** — Compare two baselines with differences highlighted.
- **UR-14** — Subscribe to notifications on changes to linked artefacts.

## Data transparency (UR-15…17)

- **UR-15** — Every result declares its source, its data age, and whether it came from federation or the graph.
- **UR-16** — Explicitly flag partial results caused by an unavailable tool.
- **UR-17** — Allow a pointwise refresh of a node from the source system.

UR-15 and UR-16 are not cosmetic. In a system that mixes live and projected data, trust depends on users always knowing what they are looking at.

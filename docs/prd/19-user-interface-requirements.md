# User interface requirements

Four distinct surfaces, with different users and usage frequencies. They must not be merged into one generic console.

| Surface | User | Frequency | Design criterion |
| ---------------------- | ------------------ | ------------------ | ------------------------ |
| Delegated dialog | Engineer | Many times a day | Speed: under 3 clicks |
| Usage console | Engineer, QA, PM | Weekly | Exploration and export |
| Configuration console | Integrator | In waves, per project | Safety and reversibility |
| Administration console | Operations | Continuously in background | Fast diagnosis |

## Delegated dialog (UI-D)

- **UI-D01** — Opens in under 2 seconds, otherwise engineers revert to copying IDs by hand.
- **UI-D02** — Incremental search with term highlighting and type filters.
- **UI-D03** — Configuration context visible and changeable at the top of the dialog.
- **UI-D04** — Multiple selection to create several links in one operation.
- **UI-D05** — In-place creation of the target when it does not exist, where the remote tool allows it.
- **UI-D06** — Visual consistency with the host tool, not with the platform brand.

## Usage console (UI-U)

- **UI-U01** — Interactive graph view with incremental expansion; never render the whole graph.
- **UI-U02** — Tabular view as an equal alternative, because many users prefer rows to nodes.
- **UI-U03** — Visual query builder with the ability to switch to SPARQL and back.
- **UI-U04** — Freshness badge on every result: live, projected with age, unavailable.
- **UI-U05** — Global configuration selector, persistent in session and always visible.
- **UI-U06** — Exports always include configuration, date and the query used.
- **UI-U07** — Permalink to every view, shareable and stable over time.
- **UI-U08** — Console views query the graph over GraphQL; the SPARQL editor is an explicit advanced mode, never the default path.
- **UI-U09** — GraphQL schema explorer available from the console, for those building their own integrations.

## Configuration console (UI-C)

- **UI-C01** — Guided wizard to register a tool, with a connection test at each step.
- **UI-C02** — Side-by-side mapping editor: native structure on the left, OSLC model on the right.
- **UI-C03** — Live mapping preview against a real sample of ten resources.
- **UI-C04** — Blocking validation with messages naming the field and the fix, not an error code.
- **UI-C05** — Diff between configuration versions, showing estimated impact on the graph.
- **UI-C06** — Promotion between environments with four-eyes approval for production.
- **UI-C07** — Rollback to a previous version in a single operation.

## Administration console (UI-A)

- **UI-A01** — Dashboard with a traffic light per adapter and propagation lag prominent.
- **UI-A02** — Drill-down from alert to specific event to original payload in three clicks.
- **UI-A03** — Error queue with bulk actions: reprocess selected, discard selected.
- **UI-A04** — Timeline of maintenance operations and configuration changes.
- **UI-A05** — Exportable and schedulable quality report.

## Cross-cutting (UI-X)

- **UI-X01** — WCAG 2.1 AA conformance.
- **UI-X02** — Italian and English interface, with a consistent domain vocabulary across both.
- **UI-X03** — Modern desktop browsers; no mobile requirement in the first release.
- **UI-X04** — Corporate single sign-on across all surfaces.

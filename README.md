# Engineering Data Integration Accelerator — starter kit

Starter kit for implementing the accelerator with Claude Code. It contains the PRD split by
chapter, the architectural decisions as ADRs, the backlog of the first increment, test
fixtures for two reference scenarios, and ready-to-use prompts. No code yet: the first
story (S00) creates the scaffold.

## Layout

```
CLAUDE.md                 rules Claude Code reads at every session
docs/prd/                 the PRD, one file per chapter (start at 00-index.md)
docs/adr/                 ADR-0001…0014 from the PRD, ADR-0015 stack (Proposed), template
docs/backlog/             increment 1 — walking skeleton (S00…S11); increment 2 — publication (P01…P09)
docs/specs/               contracts: publication envelope shape, reason codes
docs/open-decisions.md    decisions to close before or during increment 1
fixtures/skid400/         process skid: tools, artefacts, links, configurations, TTL, expected results
fixtures/aero7/           aerospace hydraulic system: same structure
fixtures/publication/     envelopes (valid, invalid) and the controlled Excel template
prompts/                  prompts to paste into Claude Code, in order
```

## How to start

1. Close the items in `docs/open-decisions.md` marked **before S00**, then set ADR-0015 to
   `Accepted` (or amend it).
2. `git init`, commit the kit as it is.
3. Open the folder in Claude Code and paste `prompts/01-plan.md`. Review the plan.
4. Paste `prompts/02-scaffold.md` to implement S00, then `prompts/03-story.md` for each
   following story, one at a time. Use `prompts/04-review.md` before merging each story.

Never put real customer data or export-controlled material in this repository without
checking your organisation's rules on AI tools first.

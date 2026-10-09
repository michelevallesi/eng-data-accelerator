An update package is in `update-2026-10-02/` at the repository root.
Read `update-2026-10-02/UPDATE.md` and apply it exactly:

1. For each file follow its action: ADD (copy if missing; if it exists and differs, show me
   the diff and wait), REPLACE (overwrite), MERGE (integrate without losing local content;
   in CLAUDE.md never touch the Commands section).
2. Do not modify any code in this step.
3. Assess retrofit stories R01–R03 against the current code: for each, answer "already
   satisfied" with evidence (file and line) or give a short plan.
4. List any conflict between the updated documents and what is already implemented.

Commit the documentation changes as `UPD-2026-10-02: docs and fixtures`, then stop for my review.

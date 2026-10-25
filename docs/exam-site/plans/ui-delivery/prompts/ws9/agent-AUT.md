# WS9 agent AUT — stage 2 (exam authoring and admin home)

You are agent **AUT** in WS9. You own exam authoring and the admin home. Your measure of success: a Master Admin (and a System Admin, within their rights) can complete **all fourteen steps of the exam creation journey** without guessing, see at every moment what is done and what is missing, and sell an exam alone or as part of a module. **Start only after the integrator says FND and API are merged.** Shahriar's main session is the **integrator**. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-aut -b ui/w9-authoring catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-aut` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row, your items, the FND components (`Stepper`, `SectionNav`, `SummaryStrip`, `RowMenu`, `StickyActionBar`, the hooks) and the API contracts.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — Part 5 findings R2, R6, R7 and items U4, U5, U6, U7, U9, the **fourteen-step journey table**, UF6, UF7, UF8, F3, F7, F9, and the UI for G3, G4, G5, G6, G13; Part 3 P1 and P5.
3. `docs/exam-site/plans/ui-delivery/README.md`, `bangla-copy.md`, `ws0-foundation.md` (Report), `docs/exam-site/ui/admin-prompt.md` (pages 5–16 and the server rules), `docs/exam-site/decisions.md` (exam rules: five-minute gap, lock after start, draft on edit).

## Order

1. **Blocker first:** the exam price section (R2 with G9), Master Admin input and System Admin read-only text, with tests.
2. U5 (numbered exam editor sections with `SectionNav`, MCQ summary, clash message naming the other exam, stored PDF view, explicit save model with the leave guard), U4 (setup-progress panel on the module page), U6 (readiness table), U9 (System Admin parity), UF7 (publish summary).
3. Type scale and P5 across all your pages.
4. P1: U7, UF6, UF8, F3, and the G4, G5, G6 UI. P2: G13 copy, F7 paste questions, F9 drag reorder (ask the integrator first; FND adds `@dnd-kit`).
5. Bangla pass on `staffCatalog`.

## Rules

- Only your files and routes. Role checks only through `useRole()`; a control the server would refuse is absent or shown as information.
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `AUT`); delete it after. Unit tests single-process.
- Do not run `next dev` or the API, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Before reporting, `git merge catalog-acquisition`; then lint 0 errors, `tsc` clean, `npm test`, `npm run build` under the lock. Walk the fourteen steps in your tests as each role. Fill in "Report — AUT" at the end of `ws9-gaps-and-polish.md` (list each journey step and where it lives), then send the ready message from `ws9-stages.md`.

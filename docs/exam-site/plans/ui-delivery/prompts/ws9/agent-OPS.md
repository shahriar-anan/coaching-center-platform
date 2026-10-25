# WS9 agent OPS — stage 2 (results, people, payments)

You are agent **OPS** in WS9. You own the exam results report, the people pages, payments, participation, the activity log and the staff account. Your measure of success: the Master Admin can answer "how did this exam go?" (participants, scores, pass rate) and "what did this student sit and score?" in two clicks, publish many results at once, and see payments and participation at a glance. **Start only after the integrator says FND and API are merged.** Shahriar's main session is the **integrator**. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-ops -b ui/w9-ops catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-ops` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row (owned files, including the new route `/staff/exams/[examId]/results`), your items, the FND components (`SummaryStrip`, `RowMenu`, hooks) and the API contracts (G14, G15, G16).
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — Part 6 core items C1, C2, C3 (bulk publish on the results report), F4, UF8; Part 3 P1 and P5.
3. `docs/exam-site/plans/ui-delivery/README.md`, `bangla-copy.md`, `ws0-foundation.md` (Report), `docs/exam-site/ui/admin-prompt.md` (pages 17–28 and the server rules; numbers only, no charts), `docs/exam-site/decisions.md`.

## Order

1. C1 (results report page: summary strip, filters, sort, table, links from participation; the module exam table link belongs to AUT, so expose a stable URL).
2. C2 (student exam history on the student page).
3. C3 bulk publish on the results report (Master Admin only, confirm dialog naming the exam and the count).
4. Type scale and P5 across your pages; F4 (payments and participation strips); UF8 row menus.
5. Bangla pass on `staffPeople`, `staffOps` and `staffResults`.

## Rules

- Only your files and routes. Role checks only through `useRole()`. Never show phone numbers to the Instructor on the results report.
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `OPS`); delete it after. Unit tests single-process.
- Do not run `next dev` or the API, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Before reporting, `git merge catalog-acquisition`; then lint 0 errors, `tsc` clean, `npm test`, `npm run build` under the lock. Fill in "Report — OPS" at the end of `ws9-gaps-and-polish.md`, then send the ready message from `ws9-stages.md`.

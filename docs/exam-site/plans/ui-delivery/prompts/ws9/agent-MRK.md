# WS9 agent MRK — stage 2 (instructor dashboard and marking)

You are agent **MRK** in WS9. You own the instructor dashboard and the marking workflow. Your measure of success: an instructor opens the site and immediately sees what is waiting, the most recently submitted papers, and their modules grouped by category; can browse category → module → exam → papers and choose what to mark; and marks each paper with the question paper and the answer side by side, then moves to the next. **Start only after the integrator says FND and API are merged.** Shahriar's main session is the **integrator**. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-mrk -b ui/w9-marking catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-mrk` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row (owned files, including moving `components/staff/people/submission-form.tsx` into `components/staff/marking/`), your items, the FND components (`SplitBrowser`, `SummaryStrip`, `ShortcutsPopover`, `RowMenu`, hooks) and the API contracts (G1, G2, G8, G10, G11, G16).
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — Part 5 findings R1, R3, R4, R5 and items U1, U2, U3, C3 (bulk publish in the browser), UF4, UF5; Part 3 P1 and P5; the business decision about instructors choosing modules (keep the current rule).
3. `docs/exam-site/plans/ui-delivery/README.md`, `bangla-copy.md`, `ws0-foundation.md` (Report), `docs/exam-site/ui/admin-prompt.md` (pages 7 and 21–24 and the marking rules), `docs/exam-site/decisions.md` (written results rules).

## Order

1. U3 (marking screen: answer PDF, question paper tab via G8, form prefilled from G1, exam info from G2, shortcuts popover).
2. U1 (instructor dashboard: summary strip, "মূল্যায়ন শুরু করুন", recent papers from G11, modules grouped by category from G10, per-exam waiting counts).
3. U2 (submissions browser with `SplitBrowser`, filters, sort, search, URL state; the old per-module route keeps working as a deep link), C3 bulk publish for the Master Admin.
4. Type scale and P5 across your pages. Then UF4, UF5. Then a Bangla pass on `staffMarking`.

## Rules

- Only your files and routes. Role checks only through `useRole()`: the Instructor never sees publish; the System Admin is read-only with no download.
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `MRK`); delete it after. Unit tests single-process.
- Do not run `next dev` or the API, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Before reporting, `git merge catalog-acquisition`; then lint 0 errors, `tsc` clean, `npm test`, `npm run build` under the lock. Fill in "Report — MRK" at the end of `ws9-gaps-and-polish.md`, then send the ready message from `ws9-stages.md`.

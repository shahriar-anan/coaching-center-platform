# Implementation prompt: student page, Part B (S1, S2, S3)

Paste everything below the line into a new session. Part A (S0 and its follow-up) is done and committed on `ui/nr01-ui`.

---

You are implementing the student page overhaul for the coaching-center exam site: steps S1 (navigation), S2 (browsing and purchase) and S3 (popular and favourites), in that order, in one run. Shahriar does the full QA at the end, so do not stop between steps unless you are blocked. Work only in `F:\cc-wt\nr01-ui`, branch `ui/nr01-ui`. Confirm first: `git -C /f/cc-wt/nr01-ui rev-parse --show-toplevel` must print `F:/cc-wt/nr01-ui`, and `git branch --show-current` must print `ui/nr01-ui`. If not, stop and report.

## Read first, in order

1. `docs/exam-site/new-requirements/student-page/plan.md`: the source of truth. Use sections 2 (decisions), 3 (working rules), 4 (steps S1 to S3) and 5 (workers). Section 4's S0 is already done.
2. `docs/exam-site/new-requirements/student-page/progress/S0-review.md`: what S0 added, the review accounts and the seeded cases.
3. `docs/exam-site/new-requirements/nr-01-ui/conversation-decisions.md`: the UI decisions, including wording, the watermark and the rules for the API.
4. `docs/exam-site/new-requirements/nr-01-ui/README.md`: the working rules.
5. `docs/exam-site/new-requirements/nr-01/contracts.md`, section 7 (compatibility rules). Do not break them.
6. `docs/exam-site/current-api.md`, the section "Added 2026-10-28 (NR-01...)".

## Setup before S1

- **Install web dependencies in the worktree.** The worktree has no `web/node_modules`. Run `npm ci` in `F:\cc-wt\nr01-ui\web`. Take the heavy lock first by writing `UI` into `F:\coaching-center-platform\.heavy.lock`, and delete it when `npm ci` ends, even on failure.
- **Copy the two local config files** into the worktree, because they are untracked and the worktree does not have them. Copy `F:\coaching-center-platform\web\.env.local` and `F:\coaching-center-platform\web\.env.development.local` to `F:\cc-wt\nr01-ui\web\`. Keep them byte-for-byte; do not add a byte-order mark, and do not edit their values. They point the web app at the API on port 8092.
- **Dev servers.** The API is already running from this worktree on port 8092, with the review database and the demo seed. Do not restart it unless it stops. The web dev server runs from the worktree after setup: stop the one running from `F:\coaching-center-platform\web`, then run `npm run dev` from `F:\cc-wt\nr01-ui\web`, and confirm http://localhost:3000 answers.

## Rules

- **Commit only. Never push.** Each sub-step is one or more commits on `ui/nr01-ui`. Shahriar's orchestrator merges the branch. End commit messages with `Co-Authored-By: Claude Haiku 5.5 <noreply@anthropic.com>`. Never use `--no-verify`.
- **Scope.** Only the files named in plan.md for each sub-step. No change to the API unless a sub-step names one. No change to the compatibility rules, to staging, or to the seed unless the plan says so.
- **Tests, as plan.md section 3 says.** Lint on the files you touch. Unit tests (vitest) for the logic named in each sub-step. One build and one headless overflow check at 320, 375 and 768 px at the end of each step, reported as text only, with no screenshots. No end-to-end tests. For Maven, use the runner only, from the worktree: `Set-Location F:\cc-wt\nr01-ui; powershell -NoProfile -File docs\exam-site\new-requirements\nr-01\tools\nr01-mvn.ps1 -Agent INT -Tests "ClassA,ClassB"`. Run it once per step, in the background, and wait for the completion notice. Do not poll. Do not run `-Full`.
- **Copy.** Every new label goes in English and Bangla. Draft the Bangla yourself. Keep a list of every new Bangla term in `progress/S1-review.md`, `S2-review.md` and `S3-review.md` under "Bangla to confirm".
- **Markers.** Where the web cannot use an API field yet, show a neutral placeholder and add `// NR-01 marker: <field>, see student-page/plan.md`. List each marker in `docs/exam-site/new-requirements/student-page/markers.md`.
- **Wording.** Student-facing "module" becomes "package", in English and Bangla. URLs keep their existing paths.
- **Layout.** Follow current LMS and ed-tech conventions (plan.md section 1). Keep the existing colours, fonts and spacing tokens. Do not change the theme.
- **Stop only if blocked.** A blocker is a decision the plan does not cover, a failing test you cannot explain, or an API field that is missing. Write it in the review note for that step, then continue with the next sub-step where you can.
- **Token care.** Search before reading. Do not read whole files over 500 lines. Keep updates to one line.

## Steps

**S1 (navigation).** Sub-steps S1.1 to S1.6 in plan.md section 4. Decisions: a top bar on desktop with Home, MCQ, Written, My exams, Results and Routine; notices bell, language, theme and the account menu on the right; the existing five-item bottom bar on phones (Home, My exams, Results, Notices, Account); Favourites in the account menu between "My account" and "Notices". "My exams" is one page with Exams and Packages tabs, reading `GET /api/v1/me/live-exam-access`, with expiry shown. Review note: `progress/S1-review.md`.

**S2 (browsing and purchase).** Sub-steps S2.1 to S2.7 in plan.md section 4. Decisions: the category page shows subcategory cards above all exams (using `branch=true`, with a switch to show only the category's own exams); every exam row shows price with the list price struck through when discounted; the exam detail shows the buy-exam button first, then every package card, and nothing where packages would be when there are none; the purchase sheet and the bundle picker show the effective price the order charges. Use `pricing`, `packages` and `access.expiresAt` from the API. Review note: `progress/S2-review.md`.

**S3 (popular and favourites).** Sub-steps S3.1 to S3.5 in plan.md section 4. Decisions: a "Popular" section on the home page, using `GET /api/v1/public/live-exam-modules?popular=true` for packages and `GET /api/v1/public/live-exams?popular=true` for exams; a popular marker on exam rows and the exam detail; a favourite toggle on exam rows, package cards and the exam detail, using `favourite` and `PUT`/`DELETE /api/v1/me/favourites/...`, where visitors are sent to login; a Favourites page with Exams and Packages tabs, linked from the account menu. Review note: `progress/S3-review.md`.

## When all three steps are done

1. Run the full web check once: `npm run lint`, `npm test` and `npm run build` in the worktree's `web/`, under the heavy lock. Report the results.
2. Commit the review notes and `markers.md`.
3. Report in five lines: sub-steps done, commit hashes, test and build results, the review note paths, and any blockers or markers.

Do not push. Shahriar will do the full QA after you report.

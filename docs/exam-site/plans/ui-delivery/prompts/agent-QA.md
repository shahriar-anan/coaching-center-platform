# QA agent — WS8 final quality check

You are the **only** agent running now. Every workstream is merged into `catalog-acquisition`. Your job is the final check a production team does before a launch: every page, every role, every business rule, and every visual detail the client could notice, in both languages and both themes, on phones and desktops. Then you fix what you safely can and report the rest.

Shahriar's main session is the **integrator**. You never merge, push or deploy, and you never touch the staging server unless the integrator asks you for Part C.

## Setup

1. Create your worktree from the main checkout (this is the one command you run there):
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/qa -b ui/ws8-qa catalog-acquisition`
2. From then on, work only inside `F:\cc-wt\qa` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. Copy `F:\coaching-center-platform\api\.env` to `F:\cc-wt\qa\api\.env`, then make your QA settings in a separate, gitignored local file (`api/.env.qa` or environment variables in your shell). Never commit or print a password.
4. `npm ci` in `F:\cc-wt\qa\web`. Install `@playwright/test` and `@axe-core/playwright` as dev dependencies on your branch, and the browsers with `npx playwright install chromium firefox webkit`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws8-final-qa.md` — **your brief**. Follow it section by section.
2. `docs/exam-site/plans/ui-delivery/README.md` — architecture, route table, UI standards, LMS practices, budgets.
3. `docs/exam-site/plans/ui-delivery/bangla-copy.md` — the copy rules you audit against.
4. `docs/exam-site/decisions.md`, `docs/exam-site/current-api.md`, and the "Rules the API enforces" / "Rules the server enforces" sections of `docs/exam-site/ui/student-flow-prompts.md` and `docs/exam-site/ui/admin-prompt.md` — the business rules.
5. The workstream briefs (`ws0`–`ws6`) and their Report sections — what was built, skipped, and flagged.

## How to work

- Unlike the building agents, **you may run the API and the production web build locally and drive real browsers** (Playwright, axe). Use a fresh local database `coaching_center_qa`, never the test database. Never `next dev`: test `npm run build` + `npm run start`.
- Take `F:\coaching-center-platform\.heavy.lock` (write `QA`) for `npm run build` and Maven runs; delete it after.
- Write the browser checks as Playwright specs under `web/e2e/` so they can be re-run. Keep screenshots out of git (add them to `.gitignore`) and zip them for Shahriar.
- Log every defect in `docs/exam-site/reports/ui-final-qa.md` as the brief describes, citing the rule or spec value each one breaks.
- Fix Minor and Polish defects yourself, and Blockers and Majors when the fix is contained. Commit each fix with a message naming the QA ID. Send anything bigger, or anything that needs a decision, to the integrator before changing it.
- Keep the README architecture rules: tokens, message catalogs, `lib/config`, no hosts in code, single-process unit tests.

## Done

When the exit criteria in the brief are met (or the only open items are ones the integrator must decide), send:

```
QA DONE: ui/ws8-qa
Last commit: <hash>
Defects: <n> found — blockers <n> (open <n>), majors <n> (open <n>), minors <n>, polish <n>
Fixed on branch: <n>; sent to integrator: <list of IDs>
Checks: lint 0 errors, tsc clean, tests <n>, playwright <n> specs in 3 browsers + 2 devices, axe 0 serious, API suite ok
Budgets: <route sizes / Lighthouse summary>
Report: docs/exam-site/reports/ui-final-qa.md
```

Then wait. After the deploy, the integrator will ask you for **Part C** on staging.

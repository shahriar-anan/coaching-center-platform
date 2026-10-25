# Agent G — WS9: close the gaps, finish the features, polish the UI

You are **agent G**, the only agent running now. All workstreams (WS0–WS6b) are merged into `catalog-acquisition` and the merged code is green. Your job: close the API gaps the building agents reported, build the planned features that were skipped, polish the UI so it looks finished and consistent everywhere, and write the dev-server guide Shahriar will use for his own typography review and manual QA.

Shahriar's main session is the **integrator**: it merges, deploys and answers questions. You never merge, push or deploy, and you never touch the staging server.

## Setup

1. Create your worktree from the main checkout (the one command you run there):
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/ws9 -b ui/ws9-gaps-polish catalog-acquisition`
2. From then on, work only inside `F:\cc-wt\ws9` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. Copy `F:\coaching-center-platform\api\.env` to `F:\cc-wt\ws9\api\.env`. Integration tests need `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`, with `TEST_DB_USER` and `TEST_DB_PASSWORD` from `api/.env` (`DB_USERNAME`, `DB_PASSWORD`). Never print the password.
4. `npm ci` in `F:\cc-wt\ws9\web`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — **your brief**. Work through it in this order: Part 3 P1 (type scale) and P6 (legacy clean-up) first, then Part 1 (G1–G4, G7), then the rest of Part 3, then Part 2 by priority, then Part 1 G5–G6, then Part 4.
2. `docs/exam-site/plans/ui-delivery/README.md` — architecture, UI standards, LMS practices, budgets.
3. `docs/exam-site/plans/ui-delivery/ws0-foundation.md` — tokens and components, including its Report (the real contracts).
4. `docs/exam-site/plans/ui-delivery/bangla-copy.md` — every Bangla string you add or touch.
5. `docs/exam-site/plans/api-completion/README.md` and `docs/exam-site/current-api.md` — API practices and what exists.
6. The client mockup `docs/exam-site/ui/client-mockup.html` (the student look to keep) and `docs/exam-site/ui/admin-prompt.md` (the staff look to keep).

## Rules

- You may change `api/` and `web/`. Keep the architecture rules: tokens only (no hex, no raw palette colors), copy only in message catalogs (both languages, Bangla by the guide), links between surfaces through `lib/config`, no hosts in code, every guarded route page inside `Suspense`.
- Polish within the two existing looks (student: client mockup; staff: admin style). Do not redesign, rename routes, or change business behavior.
- No new npm packages except `@dnd-kit/core` and `@dnd-kit/sortable` for F9, and only after the integrator says yes. Never edit Flyway `V1`–`V38`.
- Take `F:\coaching-center-platform\.heavy.lock` (write `G`) for `npm run build` and any Maven command; delete it after. Always build the API with `clean`.
- Unit tests run single-process (`npm test` does it). You may start the API and `next dev` only to verify the Part 4 guide boots, checking with `curl`; do not do a browser pass.
- Commit small, one item per commit, the message naming the item id (G1, F2, P1…) and ending with your attribution line.
- Priorities: P0 first, then P1, then P2. A finished P0 beats several half-done P2s.

## Done

All P0 items done, P1 done or explained, checks green (API suite from clean with only the known pair failing; web lint, `tsc`, tests, build), the dev QA guide written and its boot verified, and the **Report** filled in at the end of `ws9-gaps-and-polish.md`. Then send:

```
READY TO MERGE: ui/ws9-gaps-polish (agent G)
Last commit: <hash>
Checks: API suite <n> run, only BookOrderIT and CoursePurchaseIT failing; web lint 0 errors, tsc clean, tests <n> passed, build ok
Gaps closed: <G ids> | Features: <F ids> | Polish: <P ids>
Skipped: <ids and why>
API additions: <list>
Dev QA guide: docs/exam-site/reports/ui-dev-qa-guide.md (boot verified)
```

If something needs a decision (a business rule, a contract change beyond the brief, a new package), stop and ask the integrator with the file and the exact question.

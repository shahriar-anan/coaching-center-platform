# Agent B — WS1 API and demo seed, then WS6b staff operations

You are **agent B** in a team of five agents building the exam website in parallel. Shahriar's main session is the **integrator**: it merges branches, answers questions and deploys. You never merge, push or deploy, and you never touch the staging server.

- Working folder: `F:\cc-wt\ws1` (a git worktree). Stay in it.
- Phase 1 branch: `ui/ws1-api` (already checked out).
- Phase 1 brief: `docs/exam-site/plans/ui-delivery/ws1-api.md`
- Phase 2 brief (after the integrator merges WS1): `docs/exam-site/plans/ui-delivery/ws6-staff-ops.md`, the **WS6b** part only (payments, payment detail, audit log, staff account, staff leaderboard).

**Write only inside your working folder above.** Every path you create or edit must be inside it (use paths relative to it). Never write under `F:aching-center-platform` — that is the integrator's checkout — except the `.heavy.lock` file. Check with `git rev-parse --show-toplevel` before your first edit.


## Read first, in this order

1. `docs/exam-site/plans/ui-delivery/README.md` — the whole plan and "Rules for every agent".
2. `docs/exam-site/plans/ui-delivery/ws1-api.md` — your brief. Its JSON shapes are the contracts three web agents are coding against right now.
3. `docs/exam-site/plans/api-completion/README.md` — API practices that apply to every change.
4. `docs/exam-site/current-api.md` — what the API already does.

## Your job in phase 1

API only, in `api/`. Tasks A–G in `ws1-api.md`, in the order the brief gives (A, E, F first, then B, C, D, then G).

- Keep the response shapes exactly as the brief writes them. If one must change, tell the integrator before you commit it.
- The next Flyway version is `V39`. **Never edit `V1`–`V38`:** `V37` and `V38` are already applied on the staging server.
- Never put a password, key or real credential in code, tests or docs. The demo seed reads accounts from environment values.
- `api/.env` in this worktree points at the local PostgreSQL databases. Integration tests need `TEST_DB_URL`, `TEST_DB_USER` and `TEST_DB_PASSWORD`; take the user and password from `api/.env` (`DB_USERNAME`, `DB_PASSWORD`) and use `jdbc:postgresql://localhost:5432/coaching_center_test`. Never print the password.

## Rules you must follow

- Touch only `api/**`, `api/.cursor/progress/ui-delivery/**`, and the "Added for the UI delivery" section you append to `docs/exam-site/current-api.md`. Never edit `web/`.
- **Heavy lock:** before any Maven command that compiles or tests (`mvn test`, `mvn package`, `mvn clean test`), create `F:\coaching-center-platform\.heavy.lock` containing `B`. If it already exists, wait a minute and check again. Delete it when Maven ends. Run the full suite alone; it ran out of memory earlier today when run beside other heavy jobs.
- Always build with `clean` (`mvn -q clean test`), because a deleted source can leave a stale `.class` behind.
- Commit small and often on your branch, each message saying what and why, ending with the attribution line your session gives you.

## Done for phase 1

Each touched `*IT` class green, then the full suite from clean: only `BookOrderIT` and `CoursePurchaseIT` may fail (a known test-data problem). Progress notes in `api/.cursor/progress/ui-delivery/`, including `demo-seed.md` and `demo-cleanup.sql`. Fill in the **Report** section of `ws1-api.md`. Then tell the integrator:

```
READY TO MERGE: ui/ws1-api (agent B)
Last commit: <hash>
Checks: touched ITs green; full suite <run> run, only BookOrderIT and CoursePurchaseIT failing
New env names: <list, no values>
Contract changes: none | <list>
Notes for other agents: <anything they must know>
```

## Phase 2: WS6b staff operations (web)

Wait until the integrator says WS0 and WS1 are merged. Then, in the same folder:

```
git switch -c ui/ws6b-staff-ops catalog-acquisition
```

Run `npm ci` in `web/`, and run web tests with `npx vitest run --pool=forks --poolOptions.forks.singleFork=true`. Read `ws0-foundation.md` (Contracts and section 9), `bangla-copy.md`, and the WS6b rows of `ws6-staff-ops.md`. Build only the WS6b routes and files listed there, under the web rules in the README: copy in your `staffOps` namespace by `bangla-copy.md`, links to other surfaces through `lib/config`, no hosts in code, the heavy lock for `npm run build` (write `B`), no `next dev`, no browser. Whenever the integrator merges another branch, run `git merge catalog-acquisition` and run your checks again. Report with the same format, naming `ui/ws6b-staff-ops`, with `lint`, `test` and `build` results.

## When you are stuck

Ask the integrator, naming the file and the exact contract or rule in question. Do not edit another workstream's files, and do not message other agents.

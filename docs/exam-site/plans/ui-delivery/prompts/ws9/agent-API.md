# WS9 agent API — stage 1

You are agent **API** in WS9, a team of agents closing the remaining gaps and polishing the exam site. You change the API only (plus `docs/exam-site/current-api.md` and the dev-server QA guide); never edit `web/`. Shahriar's main session is the **integrator**: it merges, deploys and answers questions. You never merge, push or deploy, and never touch the staging server.

## Setup

1. Create your worktree from the main checkout (the one command you run there):
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-api -b ui/w9-api catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-api` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. Copy `F:\coaching-center-platform\api\.env` to `api/.env` in your worktree. Integration tests need `TEST_DB_URL=jdbc:postgresql://localhost:5432/coaching_center_test`, with `TEST_DB_USER` and `TEST_DB_PASSWORD` taken from `api/.env` (`DB_USERNAME`, `DB_PASSWORD`). Never print the password.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row, your items, the WS9 rules and the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — the full text of each item (search for each id: R1/G8, G1–G7, G10–G13, C1/G14, C2/G15, C3/G16, and Part 4).
3. `docs/exam-site/plans/api-completion/README.md` — API practices (Flyway only, `PageResponse`, one `ErrorCode` per condition, `404` for what a caller may not see, `*IT` on real PostgreSQL, routes in `AuditLogIT`).
4. `docs/exam-site/current-api.md`, `docs/exam-site/decisions.md`.

## Order

R1/G8 (staff question PDF, a Blocker) first. Then G1, G2, G10, G11, G14, G15, G16 (the web stage-2 agents need these). Then G3, G4, G7, G5, G6, G12, then G13. Then Part 4: write `docs/exam-site/reports/ui-dev-qa-guide.md` and verify it boots (start the API and `next dev`, `curl` the listed URLs, stop them).

## Rules

- Keep each response shape exactly as the backlog writes it; web agents code against it. If one must change, tell the integrator first.
- Never edit Flyway `V1`–`V38`; a schema change is `V39`.
- Maven only under `F:\coaching-center-platform\.heavy.lock` (write `API`), always with `clean`; delete the lock after.
- Add every new route to `AuditLogIT` and every new or changed field to `current-api.md` under "Added for the UI delivery".
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Touched `*IT` green, the full suite from clean (only `BookOrderIT` and `CoursePurchaseIT` may fail), the dev guide boot-verified, your "Report — API" section filled in at the end of `ws9-gaps-and-polish.md`. Then send the ready message from `ws9-stages.md`. Ask the integrator before any business-rule change.

# Agent C — WS3 student home, catalog, exam page, purchase

You are **agent C** in a team of five agents building the exam website in parallel. Shahriar's main session is the **integrator**: it merges branches, answers questions and deploys. You never merge, push or deploy.

- Working folder: `F:\cc-wt\ws3` (a git worktree). Stay in it.
- Branch: `ui/ws3-student-catalog` (already checked out).
- Brief: `docs/exam-site/plans/ui-delivery/ws3-student-catalog.md`

This is the first thing the client will see. Match the look of his mockup (`docs/exam-site/ui/client-mockup.html`) closely, and improve it only where your brief says.

## Read first, in this order

1. `docs/exam-site/plans/ui-delivery/README.md` — the whole plan, the route ownership table, LMS practices, and "Rules for every agent".
2. `docs/exam-site/plans/ui-delivery/ws0-foundation.md` — especially **Contracts** and section 9. You build against these before WS0 is merged.
3. `docs/exam-site/plans/ui-delivery/bangla-copy.md` — how to write Bangla. Its glossary wins over any Bangla quoted in your brief.
4. `docs/exam-site/plans/ui-delivery/ws1-api.md` tasks B and F — the new API shapes you use (type filter and counts).
5. Your brief, `ws3-student-catalog.md`, and the client mockup.
6. `web/AGENTS.md`, then the relevant guides in `web/node_modules/next/dist/docs/` before using any Next API.

## How to start before the foundation lands

Agent A (WS0) is building the shared components, shells and helpers right now. Until the integrator merges WS0:

- Write your API hooks (`web/src/lib/api/exam/catalog.ts`, `purchase.ts`) with local types and query keys prefixed `["catalog", ...]` / `["purchase", ...]`.
- Write your page components and tests in your own folders, importing the shared pieces by the exact paths and names in the WS0 Contracts. Where an import does not exist yet, put a minimal local stand-in in your own folder (for example `web/src/components/student/catalog/_tmp/`), and delete it after the merge.
- Write your copy in your own message files (`catalog`, `purchase`, `home`) in both languages.

When the integrator says WS0 is merged: run `git merge catalog-acquisition` in your branch, run `npm ci` in `web/` if `package-lock.json` changed, delete your stand-ins, switch to the real components, and fix what breaks in your own files. Do the same after WS1 is merged.

## Rules you must follow

- Touch only the files your brief lists under **Owns**, and only the routes the README gives WS3. Never edit `package.json`, shared components, shells, or another workstream's files.
- All copy in your message files, in both languages, written by `bangla-copy.md`.
- Never write a host name or absolute URL. Links to the admin panel go through `staffHref()` from `lib/config`; API calls go through `apiRequest`.
- Before `npm run build`, create `F:\coaching-center-platform\.heavy.lock` containing `C`. If it already exists, wait a minute and check again. Delete it when your build ends. `npm test` and `npm run lint` need no lock.
- Run web tests in one process: `npx vitest run --pool=forks --poolOptions.forks.singleFork=true` (optionally with your test folder). The default worker pool runs out of memory on this PC.
- Do not run `next dev`, do not run the API, do not open a browser.
- If `web/node_modules` is missing or incomplete, run `npm ci` in `web/` (the integrator may still be installing it; wait if `npm ci` is already running).
- Priorities: finish every P0 page with all its states first, then *(P1)* items, then *(P2)*.
- Commit small and often on your branch, each message saying what and why, ending with the attribution line your session gives you.

## Done

`npm run lint` 0 errors, `npm test` green, `npm run build` passes (under the lock). Every page has loading, empty, error and success states, works at 320 px and 1440 px, in Bangla and English, light and dark, and by keyboard. Fill in the **Report** section of your brief. Then tell the integrator:

```
READY TO MERGE: ui/ws3-student-catalog (agent C)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

## When you are stuck

Ask the integrator, naming the file and the exact contract or rule in question. Do not edit another workstream's files to work around a problem, and do not message other agents.

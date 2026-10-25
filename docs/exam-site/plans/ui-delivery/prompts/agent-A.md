# Agent A — WS0 foundation, then WS2 student entrance

You are **agent A** in a team of five agents building the exam website in parallel. Shahriar's main session is the **integrator**: it merges branches, answers questions and deploys. You never merge, push or deploy.

- Working folder: `F:\cc-wt\ws0` (a git worktree). Stay in it.
- Phase 1 branch: `ui/ws0-foundation` (already checked out).
- Phase 1 brief: `docs/exam-site/plans/ui-delivery/ws0-foundation.md`
- Phase 2 brief (after the integrator merges WS0): `docs/exam-site/plans/ui-delivery/ws2-student-entrance.md`

**Write only inside your working folder above.** Every path you create or edit must be inside it (use paths relative to it). Never write under `F:aching-center-platform` — that is the integrator's checkout — except the `.heavy.lock` file. Check with `git rev-parse --show-toplevel` before your first edit.


## Read first, in this order

1. `docs/exam-site/plans/ui-delivery/README.md` — the whole plan, the route ownership table, and "Rules for every agent".
2. `docs/exam-site/plans/ui-delivery/ws0-foundation.md` — your brief, including **Contracts** and section 9 (environments and URLs).
3. `docs/exam-site/plans/ui-delivery/bangla-copy.md` — how to write Bangla.
4. `web/AGENTS.md`, then the relevant guides in `web/node_modules/next/dist/docs/` before using any Next API.

## Your job in phase 1

You are the foundation everyone else builds on, and three agents start building against your **Contracts** right now.

1. First commit: add every dependency from the README list (including `pdfjs-dist`), and create the contract files as working stubs with the exact paths, export names and props in the Contracts section: components, shells, `lib/config`, guards, formatting, clock, list params, test helpers, the message namespace files (all registered in `messages/index.ts`), and the `NoticeBell` and `PdfViewer` stubs. Tell the integrator as soon as this commit exists.
2. Then build the real implementations: tokens and fonts, shells (including the bottom tab bar), components, auth routing, environments and host routing (section 9), the old-admin nav cleanup, the `proxy.ts` redirect removal, and the `/sandbox` and `/live-exams` removal.
3. Never rename or remove a contract name or prop after the first commit. If one must change, ask the integrator first.

## Rules you must follow

- Touch only the files your brief lists under **Owns**. Only you edit `web/package.json` and the lockfile.
- All copy goes in message files, in both languages, written by `bangla-copy.md`.
- Never write a host name or absolute URL. Use `lib/config`.
- Before `npm run build` (or anything heavy), create `F:\coaching-center-platform\.heavy.lock` containing `A`. If it already exists, wait a minute and check again. Delete it when your build ends. `npm test` and `npm run lint` need no lock.
- Run web tests in one process: `npx vitest run --pool=forks --poolOptions.forks.singleFork=true` (optionally with your test folder). The default worker pool runs out of memory on this PC.
- Do not run `next dev`, do not run the API, do not open a browser.
- If `web/node_modules` is missing or incomplete, run `npm ci` in `web/` (the integrator may still be installing it; wait if `npm ci` is already running).
- Commit small and often on your branch, each message saying what and why, ending with the attribution line your session gives you.

## Done for phase 1

`npm run lint` 0 errors, `npm test` green, `npm run build` passes (under the lock). Fill in the **Report** section at the end of `ws0-foundation.md`. Then tell the integrator, in this format:

```
READY TO MERGE: ui/ws0-foundation (agent A)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
Contract changes: none | <list>
Notes for other agents: <anything they must know>
```

## Phase 2: WS2 student entrance

Wait until the integrator says WS0 is merged. Then, in the same folder:

```
git switch -c ui/ws2-student-entrance catalog-acquisition
```

Worktrees share branches with the main checkout, so `catalog-acquisition` already includes the merge. If `git switch` complains about local changes, commit them first; your WS0 work is already merged. Run `npm ci` in `web/` if `package-lock.json` changed. Read `ws2-student-entrance.md` and build it under the same rules. Whenever the integrator merges another branch, run `git merge catalog-acquisition` in your branch and run your checks again. Report with the same format, naming `ui/ws2-student-entrance`.

## When you are stuck

Ask the integrator, naming the file and the exact contract or rule in question. Do not edit another workstream's files to work around a problem, and do not message other agents.

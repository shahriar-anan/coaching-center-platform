# Agent E — WS5 staff homes and catalog, then WS6a staff people and marking

You are **agent E** in a team of five agents building the exam website in parallel. Shahriar's main session is the **integrator**: it merges branches, answers questions and deploys. You never merge, push or deploy.

- Working folder: `F:\cc-wt\ws5` (a git worktree). Stay in it.
- Phase 1 branch: `ui/ws5-staff-catalog` (already checked out).
- Phase 1 brief: `docs/exam-site/plans/ui-delivery/ws5-staff-catalog.md`
- Phase 2 brief (after the integrator merges WS5): `docs/exam-site/plans/ui-delivery/ws6-staff-ops.md`, the **WS6a** part only (students, instructors, system admins, participation, submissions and marking).

The client's Master Admin will run his exams on these screens. They follow the admin-prompt look (calm, dense, blue on grey, desktop first, usable on a phone), not the student look.

## Read first, in this order

1. `docs/exam-site/plans/ui-delivery/README.md` — the whole plan, the route ownership table, LMS practices, and "Rules for every agent".
2. `docs/exam-site/plans/ui-delivery/ws0-foundation.md` — especially **Contracts** (including `useRole`, `Table`, `ConfirmDialog`, `PdfViewer`) and section 9. You build against these before WS0 is merged.
3. `docs/exam-site/plans/ui-delivery/bangla-copy.md` — how to write Bangla. Its glossary wins over any Bangla quoted in your brief (for example সরাসরি ভর্তি, not রোস্টার; শিক্ষক, not ইনস্ট্রাক্টর; খসড়া, not ড্রাফট).
4. `docs/exam-site/plans/ui-delivery/ws1-api.md` tasks C and E — the roster member list and the payments permission.
5. Your brief, `ws5-staff-catalog.md`, and pages 5–16, 29 and 30 of `docs/exam-site/ui/admin-prompt.md` (its "Rules the server enforces" are binding).
6. `web/AGENTS.md`, then the relevant guides in `web/node_modules/next/dist/docs/` before using any Next API.

## How to start before the foundation lands

Agent A (WS0) is building the shared components, shells and helpers right now. Until the integrator merges WS0:

- Write your API hooks (`web/src/lib/api/exam/staff-catalog.ts`, `staff-home.ts`) with local types and query keys prefixed `["staffCatalog", ...]` / `["staffHome", ...]`.
- Write your page components and tests in your own folders, importing shared pieces by the exact paths and names in the WS0 Contracts. Where an import does not exist yet, put a minimal local stand-in in your own folder (for example `web/src/components/staff/catalog/_tmp/`), and delete it after the merge.
- Import `PdfViewer` from `@/components/pdf/pdf-viewer` from the start; WS4 replaces the stub later without changing its props.
- Write your copy in your own message file (`staffCatalog`) in both languages.

When the integrator says WS0 is merged: run `git merge catalog-acquisition` in your branch, run `npm ci` in `web/` if `package-lock.json` changed, delete your stand-ins, switch to the real components, and fix what breaks in your own files. Do the same after WS1 and WS4 are merged.

## Rules you must follow

- Touch only the files your brief lists under **Owns**, and only the routes the README gives WS5. Never edit `package.json`, shared components, shells, or another workstream's files.
- Role checks only through `useRole()`. A control the server would refuse for this role is absent.
- All copy in your message files, in both languages, written by `bangla-copy.md`.
- Never write a host name or absolute URL. Links to the student site go through `siteHref()` from `lib/config`; API calls go through `apiRequest` and `apiUpload`.
- Before `npm run build`, create `F:\coaching-center-platform\.heavy.lock` containing `E`. If it already exists, wait a minute and check again. Delete it when your build ends. `npm test` and `npm run lint` need no lock.
- Do not run `next dev`, do not run the API, do not open a browser.
- If `web/node_modules` is missing or incomplete, run `npm ci` in `web/` (the integrator may still be installing it; wait if `npm ci` is already running).
- Priorities: finish every P0 page with all its states first, then *(P1)* items, then *(P2)*.
- Commit small and often on your branch, each message saying what and why, ending with the attribution line your session gives you.

## Done for phase 1

`npm run lint` 0 errors, `npm test` green, `npm run build` passes (under the lock). Every page has loading, empty, error and success states, works at 1440 px and 375 px, in Bangla and English, light and dark, and by keyboard, for each of the three staff roles. Fill in the **Report** section of `ws5-staff-catalog.md`. Then tell the integrator:

```
READY TO MERGE: ui/ws5-staff-catalog (agent E)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

## Phase 2: WS6a staff people and marking

Wait until the integrator says WS5 is merged. Then, in the same folder:

```
git switch -c ui/ws6a-staff-people catalog-acquisition
```

Run `npm ci` in `web/` if `package-lock.json` changed. Read the WS6a rows and the "LMS additions" of `ws6-staff-ops.md` and pages 17–24 of `admin-prompt.md`. Build only the WS6a routes and files, under the same rules (copy in the `staffPeople` namespace). Link to payments only by URL (`/staff/payments?tranId=`), never by importing WS6b files. Whenever the integrator merges another branch, run `git merge catalog-acquisition` and run your checks again. Report with the same format, naming `ui/ws6a-staff-people`.

## When you are stuck

Ask the integrator, naming the file and the exact contract or rule in question. Do not edit another workstream's files to work around a problem, and do not message other agents.

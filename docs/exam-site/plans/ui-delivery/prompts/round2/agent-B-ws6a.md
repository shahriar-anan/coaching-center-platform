# Agent B — round 3: WS6a staff people, participation and marking

WS6b is merged (`61d7eed`), thank you. The audit log now lists newest first (`3aa000c`). Agent E is still on WS5, so **you take WS6a**, which was planned for E. E will not do it.

Work only inside `F:\cc-wt\ws1` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the lock file.

## Setup

```
git switch -c ui/ws6a-staff-people catalog-acquisition
```

`catalog-acquisition` already contains WS0, WS1, WS2, WS3, WS4 and your WS6b. Run `npm ci` in `web/` only if `package-lock.json` changed.

## Read

- `docs/exam-site/plans/ui-delivery/ws6-staff-ops.md`: the **WS6a** rows, its "LMS additions" (marking queue, side-by-side marking) and Rules. WS6a owns `staff/students/**`, `staff/instructors/**`, `staff/admins/**`, `staff/participation/**`, `staff/submissions/**`, `components/staff/people/**`, `lib/api/exam/staff-people.ts`, the `staffPeople` message files, and `tests/ws6a/**`.
- Pages 17–24 of `docs/exam-site/ui/admin-prompt.md` (their server rules are binding).
- `docs/exam-site/plans/ui-delivery/bangla-copy.md` (শিক্ষক, সরাসরি ভর্তি, খসড়া নম্বর সেভ করুন, ফলাফল প্রকাশ করুন, মূল্যায়ন বাকি, প্রকাশ বাকি, খাতা জমা হয়নি, তাই নম্বর দেওয়া যাবে না।).

## Build (P0 first)

- `/staff/students` (search, create with the one-time code dialog, status, reissue, delete for Master Admin) and `/staff/students/[id]` with the access list from `GET /api/v1/students/{id}/live-exam-access` (reasons সরাসরি ভর্তি / মডিউল কেনা / পরীক্ষা কেনা, links to the module and to `/staff/payments?tranId=`).
- `/staff/instructors` (Master Admin): search, status and assigned filters, create, reissue.
- `/staff/admins` (Master Admin): paged list, create, status, reissue, delete; the Master Admin is never a row.
- `/staff/participation` (all staff; filters in the URL; MCQ rows show "—" in written-only columns).
- `/staff/submissions`, `/staff/submissions/m/[moduleId]`, `/staff/submissions/[participantId]`: the per-role marking screen (Instructor and Master Admin save a draft with marks, comment and the marked PDF through `apiUpload`; Master Admin publishes; System Admin is read-only; no form without an answer). Use `PdfViewer` from `@/components/pdf/pdf-viewer` with `allowDownload` for staff.
- Then `(P1)`: marking queue (previous/next, "সেভ করে পরেরটি", J/K) and side-by-side marking.

Every route page wraps its content in `<Suspense fallback={<Skeleton variant="card" />}>`. Role checks only through `useRole()`. Links to WS5 pages (`/staff/modules/...`) and WS6b pages (`/staff/payments...`) by URL only.

## Checks and report

From `web/`: `npm run lint` 0 errors, `npm test`, `npm run build` under the lock (`F:\coaching-center-platform\.heavy.lock` containing `B`; wait while it exists; delete after). Before reporting, run `git merge catalog-acquisition` again. Add a "WS6a Report" section at the end of `ws6-staff-ops.md`, commit, then send:

```
READY TO MERGE: ui/ws6a-staff-people (agent B)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything>
```

Do not run `next dev` or the API, do not open a browser, do not edit files outside the WS6a Owns list.

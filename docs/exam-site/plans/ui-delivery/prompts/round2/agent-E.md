# Agent E — round 2: build WS5, then WS6a

State when this was written (12:44, 25 October): your branch `ui/ws5-staff-catalog` in `F:\cc-wt\ws5` is up to date with `catalog-acquisition` (WS0 and WS1 merged), with hooks committed and `web/src/components/staff/` started but uncommitted.

Work only inside `F:\cc-wt\ws5` (check `git rev-parse --show-toplevel`). Read-only reference: `docs/exam-site/plans/ui-delivery/prompts/agent-E.md`, your brief `ws5-staff-catalog.md`, and pages 5–16, 29 and 30 of `docs/exam-site/ui/admin-prompt.md`.

1. Commit your current work.
2. Build on the real WS0 modules (no `_tmp` stand-ins): `@/components/ui/*`, `useRole` from `@/lib/auth/guards`, `@/lib/config`, `@/lib/format`. The real contract details are in the **Report** section of `ws0-foundation.md`. `StaffShell` takes no title; pages render `PageHeader`.
3. P0 pages:
   - `/staff` home for Master Admin, System Admin and Instructor
   - categories tree and editor
   - modules list and editor with the বিবরণ, পরীক্ষা, সরাসরি ভর্তি (roster) and শিক্ষক (instructor) tabs. The roster tab uses the merged `GET /api/v1/live-exam-modules/{moduleId}/roster`.
   - MCQ and written exam editors. Use `PdfViewer` from `@/components/pdf/pdf-viewer` for the written question PDF.
   Then `(P1)`, then `(P2)`.
4. Bangla by `docs/exam-site/plans/ui-delivery/bangla-copy.md`: খসড়া, চূড়ান্ত করুন, প্রকাশ করুন, সরাসরি ভর্তি, শিক্ষক, মূল্যায়ন বাকি, প্রকাশ বাকি, শর্টকাট.
5. Before reporting, run `git merge catalog-acquisition` (WS4's real PdfViewer and WS3 may be merged by then). Then run the checks from `web/`:
   - `npm run lint` (0 errors).
   - `npm test`.
   - `npm run build` under the lock: `F:\coaching-center-platform\.heavy.lock` containing `E`; wait while it exists; delete it after.
   Each page must work for all three staff roles, at 1440 px and 375 px, in both languages and themes.
6. Fill in the **Report** in `ws5-staff-catalog.md`, commit, then send:

```
READY TO MERGE: ui/ws5-staff-catalog (agent E)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

7. **WS6a, only after the integrator says WS5 is merged:** in the same folder, `git switch -c ui/ws6a-staff-people catalog-acquisition`, then follow the "Phase 2" section of `prompts/agent-E.md` (students, instructors, admins, participation, submissions and marking). Report as `ui/ws6a-staff-people`.

**Build tip from WS4:** `RequireStudent` and `RequireStaff` use `useSearchParams`, so wrap every route page that renders them in `<Suspense fallback={<Skeleton variant="card" />}>` (Skeleton from `@/components/ui/skeleton`), or the build fails on prerender.

Do not run `next dev` or the API, do not open a browser, do not edit files outside your Owns list.

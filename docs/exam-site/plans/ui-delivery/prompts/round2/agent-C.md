# Agent C — round 2: finish WS3 on the real foundation and report

State when this was written (12:44, 25 October): your branch `ui/ws3-student-catalog` in `F:\cc-wt\ws3` has WS0 merged (`1004cdb`) and 15 uncommitted files where you were replacing the `_tmp` stand-ins with the real WS0 modules. It is **9 commits behind** `catalog-acquisition`, which now also has the WS1 API (examType filter, `mcqCount`/`writtenCount`, `/me/live-exam-orders`), single-process tests, and new plan rules.

Work only inside `F:\cc-wt\ws3` (check `git rev-parse --show-toplevel`). Read-only reference: `docs/exam-site/plans/ui-delivery/prompts/agent-C.md` and your brief `ws3-student-catalog.md`.

1. Finish the switch to the real WS0 modules and delete `web/src/components/student/catalog/_tmp/` completely. Commit.
2. Run `git merge catalog-acquisition`. A conflict outside your own files means stop and tell the integrator. Run `npm ci` in `web/` if `package-lock.json` changed.
3. Clean-ups from your own report:
   - Use `phoneRequired` and `emailVerifiedAt` from the real `Me` in `@/lib/api/exam/types` (no casts).
   - Use `staffHref()` from the real `@/lib/config` for any link to the admin panel.
   - The WS1 filter is merged. A harmless retry-without-`examType` fallback may stay.
   - Bangla by `docs/exam-site/plans/ui-delivery/bangla-copy.md`: চালু আছে (not অ্যাক্সেস আছে), পুরো মডিউল কিনুন, পেমেন্ট করুন, মূল্য এখনো ঠিক হয়নি, তারিখ পরে জানানো হবে, অনুশীলন হিসেবে দিন, নিরাপদ পেমেন্ট, রিফান্ড নীতি. Read it aloud once.
   - Welcome card (P1): WS2 sets `localStorage` `exam-portal.welcome=1` on registration; show it once on the home, then clear it.
4. Checks from `web/`:
   - `npm run lint` (0 errors).
   - `npm test`.
   - `npm run build` under the lock: `F:\coaching-center-platform\.heavy.lock` containing `C`; wait while it exists; delete it after. Your build must pass now (it never ran before).
5. Update your **Report** in `ws3-student-catalog.md`, commit, then send:

```
READY TO MERGE: ui/ws3-student-catalog (agent C)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

Do not run `next dev` or the API, do not open a browser, do not edit files outside your Owns list.

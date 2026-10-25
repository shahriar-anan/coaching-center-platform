# Agent B — round 2: finish WS6b and report

State when this was written (12:44, 25 October): your files are now in your own worktree `F:\cc-wt\ws1` on branch `ui/ws6b-staff-ops` (committed as "Move the WS6b work into its own worktree"), up to date with `catalog-acquisition`. The payments pages are partly edited, and `account`, `audit`, `exams/[examId]/leaderboard` and `tests/ws6b` are uncommitted. The integrator's checkout is clean again; thank you.

Work only inside `F:\cc-wt\ws1` (check `git rev-parse --show-toplevel`). **Never write under `F:\coaching-center-platform\`** except the lock file. Read-only reference: `docs/exam-site/plans/ui-delivery/prompts/agent-B.md` (Phase 2 section) and the WS6b rows of `ws6-staff-ops.md`.

1. Commit your current work. If `F:\cc-wt\ws1\web\node_modules` is missing, run `npm ci` in `F:\cc-wt\ws1\web` first.
2. Finish the WS6b P0 pages:
   - `/staff/payments` and the payment detail (search, status filter, method, access, copy buttons; System Admin read-only)
   - `/staff/audit` (Master Admin)
   - `/staff/account`
   - `/staff/exams/[examId]/leaderboard`
   Link to students only by URL (`/staff/students/[id]`). Then `(P1)`, then `(P2)`.
3. Bangla by `docs/exam-site/plans/ui-delivery/bangla-copy.md`: অপেক্ষমাণ / সফল / ব্যর্থ / বাতিল, লেনদেন নম্বর, পেমেন্ট মাধ্যম, কার্যক্রম লগ, আমার অ্যাকাউন্ট, পেমেন্ট নিশ্চিত হলে পরীক্ষাটি চালু হবে, পেমেন্ট ছাড়া চালু করতে সরাসরি ভর্তি করুন.
4. Before reporting, run `git merge catalog-acquisition` (others may be merged by then). Then run the checks from `F:\cc-wt\ws1\web`:
   - `npm run lint` (0 errors).
   - `npm test`.
   - `npm run build` under the lock: `F:\coaching-center-platform\.heavy.lock` containing `B`; wait while it exists; delete it after.
5. Fill in a **WS6b Report** section at the end of `ws6-staff-ops.md` (in your worktree), commit, then send:

```
READY TO MERGE: ui/ws6b-staff-ops (agent B)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

**Build tip from WS4:** `RequireStudent` and `RequireStaff` use `useSearchParams`, so wrap every route page that renders them in `<Suspense fallback={<Skeleton variant="card" />}>` (Skeleton from `@/components/ui/skeleton`), or the build fails on prerender.

Do not run `next dev` or the API, do not open a browser, do not edit files outside your Owns list.

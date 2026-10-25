# Agent A — round 2: finish WS2 and report

State when this was written (12:44, 25 October): you are in `F:\cc-wt\ws0` on branch `ui/ws2-student-entrance`, up to date with `catalog-acquisition`, with a WIP commit and about 12 uncommitted files (register, account payments, the policy pages, message files).

Work only inside `F:\cc-wt\ws0` (check `git rev-parse --show-toplevel`). Read-only reference: `docs/exam-site/plans/ui-delivery/prompts/agent-A.md` and your brief `ws2-student-entrance.md`.

1. Commit your current work.
2. Finish the P0 pages in your brief:
   - `/login`, `/login/google`, `/register`, `/forgot-password`, `/reset-password`, `/activate`, `/set-phone`
   - `/account`, `/account/payments` (uses `GET /api/v1/me/live-exam-orders`, now merged)
   - `/notices` and the NoticeBell
   - `/help`, `/about`, `/contact`, `/terms`, `/privacy`, `/refund`
3. Delete the old `app/login`, `app/register`, `app/forgot-password`, `app/reset-password`, `app/activate` and `app/account` once their replacements exist, so no route is defined twice. The build fails on duplicate routes.
4. On registration success, set `localStorage` `exam-portal.welcome=1` (WS3 shows the welcome card from it).
5. Bangla by `docs/exam-site/plans/ui-delivery/bangla-copy.md`: লগইন (not প্রবেশ), অ্যাকাউন্ট চালু করুন, the rewritten auth sentences, and the policy banner "নমুনা লেখা — চূড়ান্ত লেখা শীঘ্রই যোগ হবে".
6. Before reporting, run `git merge catalog-acquisition` again (the integrator may have merged WS4 or WS3 by then). Then run the checks from `web/`:
   - `npm run lint` (0 errors).
   - `npm test`.
   - `npm run build` under the lock: `F:\coaching-center-platform\.heavy.lock` containing `A`; wait while it exists; delete it after.
7. Fill in the **Report** in `ws2-student-entrance.md`, commit, then send:

```
READY TO MERGE: ui/ws2-student-entrance (agent A)
Last commit: <hash>
Checks: lint 0 errors, tests <n> passed, build ok
P1 done: <list> | P2 done: <list> | skipped: <list>
Contract issues: none | <list>
Notes for other agents: <anything they must know>
```

Do not run `next dev` or the API, do not open a browser, do not edit files outside your Owns list.

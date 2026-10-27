# Launch prompt: STU follow-up

You are agent **STU** on a follow-up to WS9. Your branch is `ui/w9-student` in `F:\cc-wt\w9-stu`.

## Setup

1. In your worktree: `git merge catalog-acquisition`.
2. Work only inside `F:\cc-wt\w9-stu`. Never write under `F:\coaching-center-platform\` except the lock file `.heavy.lock`.

## Read

- `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md`: "Report — STU" and its addenda 1 to 4.
- `docs/exam-site/plans/ui-delivery/bangla-copy.md`.

## Items (one commit per item; the message starts with the ID)

1. **STU-FU-1 Bangla pass.** Re-read the Bangla strings in `web/src/i18n/messages/bn/` for `auth`, `account`, `legal`, `help` and `contact`. Check the forbidden terms, one term per concept, and Bengali digits through the formatters. Fix wording only.
2. **STU-FU-2 320 px and dark-mode review, by reading the code.** There is no device. Look for fixed widths that overflow at 320 px, flex children without `min-w-0`, and colours that bypass the tokens in dark mode. Fix what you find in files you own, one commit per finding. In your report, list what you could not check without a device.
3. **STU-FU-3** `components/student/sitting/mcq-paper.tsx` line 481 uses `text-2xl`. Replace it with the FND scale class for that role.
4. **STU-FU-4 decision.** Keep the sitting jump grid at 44 px (the README's touch target). Record it in your report as "accepted by STU, pending user confirmation".
5. **STU-FU-5 unused message keys (from FND's clean-up).** In the namespaces you may touch now (`auth`, `account`, `legal`, `help`, `contact`, `home`), remove keys with no use in `web/src` (check every literal and template key). One commit. Do not remove keys in `catalog`, `purchase`, `notices`, `result` or `sitting` (phase 2 owners will do that). The FND report counts 48 unused keys in the STU, AUT, OPS and MRK namespaces; the rest of that list belongs to the other agents.

## Do not touch

These files belong to the phase 2 student agents, and editing them now would conflict:
- `components/student/catalog/**` (including `module-page.tsx`, which has a `text-2xl` that STU-PUR will fix)
- `components/student/purchase/**`
- `components/student/notice-bell.tsx`
- `app/(site)/notices/**`
- `components/student/result/**`
- `components/student/home/**`
- `lib/api/exam/catalog.ts`, `purchase.ts`, `notices.ts`, `sitting.ts`
- the messages `catalog`, `purchase`, `notices`, `result`, `sitting`

Not yours: ST-2 (waits for the API flag and STU-PUR) and ST-4 (the integrator adds the status to `ApiError`).

## Checks

- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process).
- `npm run build` only while `F:\coaching-center-platform\.heavy.lock` holds `STU`. Write `STU` to that file, build, then delete the file. If another name is in the file, wait and do not delete it.

## Done

1. Add "Addendum 5 — STU, WS9 follow-up" to "Report — STU" on your branch.
2. Send:

```
READY TO MERGE: ui/w9-student (agent STU, follow-up)
Last commit: <hash>
Checks: lint <…>, tsc <…>, npm test <…>, build <…> under lock STU
Items done: <IDs> | not done: <IDs and why>
```

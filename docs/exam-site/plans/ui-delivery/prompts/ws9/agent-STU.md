# WS9 agent STU — stage 1 (student site)

You are agent **STU** in WS9. You own the student site and the auth pages. Keep the client mockup look and polish within it so it looks finished on a phone and a desktop. Shahriar's main session is the **integrator**. You never merge, push or deploy.

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/w9-stu -b ui/w9-student catalog-acquisition`
2. Work only inside `F:\cc-wt\w9-stu` (check `git rev-parse --show-toplevel`). Never write under `F:\coaching-center-platform\` except the `.heavy.lock` file.
3. `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/ws9-stages.md` — your row (owned files), your items, the FND contracts (type scale, `StickyActionBar`, `PrintArea`, `SummaryStrip`) and the API contracts, the rules and the ready message.
2. `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` — U10, U11, U12, G7, F5, F6, UF1, UF2, UF3, Part 3 P1 (scale) and P4 (student details), F10.
3. `docs/exam-site/plans/ui-delivery/README.md`, `bangla-copy.md`, `ws0-foundation.md` (Report), the client mockup `docs/exam-site/ui/client-mockup.html`, and `docs/exam-site/ui/student-flow-prompts.md`.

## Order

P0 first: apply the type scale to every student page (content never under 16 px; replace all `text-[..]` and review every `text-sm`), P4 details, UF3, G7 (name edit; the API arrives from agent API; handle a 404 gracefully until then). Then P1: U10, U11, F5, F6, UF1, UF2, F10. Then P2: U12. Finish with a Bangla pass on your message files.

FND's components may not be merged when you start: import them by the exact contract paths and use a local stand-in in your own folder until the integrator says FND is merged; then `git merge catalog-acquisition`, delete the stand-ins, and fix what breaks in your files.

## Rules

- Only your files and routes. Copy only in your message namespaces, written by the copy guide. Links to the admin panel through `lib/config`.
- `npm run build` only under `F:\coaching-center-platform\.heavy.lock` (write `STU`); delete it after. Unit tests single-process.
- Do not run `next dev` or the API, do not open a browser.
- Commit small, one item per commit, naming the item id, ending with your attribution line.

## Done

Before reporting, `git merge catalog-acquisition`; then lint 0 errors, `tsc` clean, `npm test`, `npm run build` under the lock. Fill in "Report — STU" at the end of `ws9-gaps-and-polish.md`, then send the ready message from `ws9-stages.md`.

## Follow-up from the integrator's audit (25 Oct, branch at `7451722`)

Continue on `ui/w9-student` in `F:\cc-wt\w9-stu`. The API checks out: G7 `PATCH /api/v1/me` returns `Me`, and the G12 rows carry `moduleTitleEn`/`moduleTitleBn`, `liveAt`, `endAt` and `price`. Do the items below, one commit per id, then add an "Addendum — audit follow-up" to "Report — STU".

**Now (no dependency)**
- [ ] **ST-1 (Major) U10 search ignores the door.** `useModuleSearch(term)` in `lib/api/exam/catalog.ts` does not send `examType`, so the MCQ door lists written modules (and the other way round), and `door-discovery.tsx` links them under the wrong door path. Pass `examType` (from `doorType(door)`) to the call and to the query key; the API already filters by it together with `q`. Add a test where a module of the other type is not shown.
- [ ] **ST-2 (Minor) U10 upcoming price word.** When `price` is null, the strip shows "মূল্য এখনো ঠিক হয়নি". For this endpoint, null means the exam is sold with its module only. Follow U10: show "চালু আছে" when `liveAt ≤ now < endAt`; otherwise show the price, or a module-only word for null (add the key in `catalog`, written by the copy guide). Never show "not set" here.
- [ ] **ST-3 (Minor) UF1 student on the receipt.** UF1 lists the student on the receipt; it is missing. Add the student's name and student code from the session user.
- [ ] **ST-4 (Minor) G7 error messages.** A `400 VALIDATION_FAILED` from `PATCH /me` shows the generic error. Map it to `account.profile.nameInvalid`. Also, an older API answers `PATCH /me` with 405 (because `GET /me` exists), not 404, so the "coming soon" branch never runs. Once API is merged, remove `nameNotReady` and that branch.
- [ ] **ST-5 (Minor) P4 items that are in your own files.** `components/student/catalog/status-pill.tsx` is yours: add `whitespace-nowrap` so pills stay on one line. `Stat` already exists in `@/components/ui/stat` (no FND work needed): use it for the results three-stat row with numbers in gold. Add the routine table's edge fade inside its card on the page. Then do the dark-mode and 320 px checks by reading the classes, as far as possible without a browser, and list what you could not check.
- [ ] **ST-6 F10 remaining tests.** Cover a deadline passing during an upload (assert with `await expect(promise).rejects…` instead of letting the `ApiError` escape the test body), take and retake redirects, and the offline flush beyond MCQ backup.

**After the integrator says FND is merged** (`git merge catalog-acquisition`)
- [ ] **ST-7 (Major) Sticky action bar.** Switch `exam-page.tsx` to `@/components/ui/sticky-action-bar` and delete `components/student/stand-ins/`. Your stand-in sits under the student tab bar on phones (both are fixed at the bottom; the tab bar is `z-30`), so a signed-in student cannot reach the primary button. FND fixes the offset in FN-2. Check the result: the exam page at 375 px, signed in, with the primary button visible above the tab bar.
- [ ] **ST-8 UF1 print.** Wrap the receipt card in `PrintArea`, so the shell is hidden in print.
- [ ] **ST-9 Merge conflict.** `components/student/notice-bell.tsx` is yours; FND reverts its edit (FN-4). If the conflict still appears, keep your line.

**Accepted as is:** the sitting jump grid stays at 44 px cells (the README touch target wins over P4's 40 px).

### Status at the 25 Oct check (head `4608f6e`)

Done and checked in code: ST-5 for the stat numbers (`Stat` is used on the results pages), ST-6 for the take and retake redirects and the offline flush. **Still open, treat as immediate, in this order:**
1. **ST-1 (Major), not done.** `useModuleSearch` in `lib/api/exam/catalog.ts` still takes only `q`, and `door-discovery.tsx` still calls it without the door type. Add `examType` to the function, the query key and the call, and add the test.
2. **ST-3 (Minor, required by UF1), not done.** The receipt still has no student name or code. Add them.
3. **ST-2 (Minor), not done.** `door-discovery.tsx` still shows "মূল্য এখনো ঠিক হয়নি" when the price is null. Use the live and module-only words as written above.
4. **ST-4 (Minor), not done.** `account/page.tsx` still maps every error except 404 to the generic message. Map `VALIDATION_FAILED` to `nameInvalid`, and handle `405` as well as `404` as "not deployed".
5. **ST-8 (Minor), not done.** `receipt-view.tsx` does not use `PrintArea`. Use it after FND merges, which it now has.
6. **ST-5 status pill (Minor), not done.** Add `whitespace-nowrap` to `status-pill.tsx`.
7. **Notice bell (FN handover), not done.** `components/student/notice-bell.tsx` still has `text-white`. Change it to `text-on-warn`.
8. **ST-7 (Major), waits on the integrator.** FND is now merged into `catalog-acquisition`. Run `git merge catalog-acquisition` in your worktree, delete `components/student/stand-ins/`, and point `exam-page.tsx` at `@/components/ui/sticky-action-bar`. Check the exam page at 375 px, signed in.

STU is held from the merge until items 1 to 8 are committed.

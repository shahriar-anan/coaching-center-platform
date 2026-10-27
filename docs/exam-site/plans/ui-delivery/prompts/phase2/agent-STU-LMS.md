# Agent STU-LMS (phase 2): notices, results, my modules, written papers

You are agent **STU-LMS**. You make the student's notice box show every notice the API sends, with the right labels, and you finish the result and written-paper behaviour that the decisions require. Read [README.md](README.md) first (rules, lock, ready message).

## Setup

1. `git -C F:/coaching-center-platform worktree add F:/cc-wt/stu-lms -b ui/stu-lms catalog-acquisition`
2. Work only inside `F:\cc-wt\stu-lms`. Run `npm ci` in `web/`.

## Read, in this order

1. `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: section 4 (S-002, S-004, S-017, S-018, S-020, S-024), section 6.2 (notice kinds), 6.3 (answer release), 6.4 (announcements), 6.5 (late uploads), section 8 (the contract for the review and the notice kinds), section 9 (your row).
2. `docs/exam-site/ui/student-flow-prompts.md`: "Notices" (Prompt 2) and the result copy in the MCQ and written offers.
3. `docs/exam-site/ui/client-mockup.html` (the look) and `docs/exam-site/plans/ui-delivery/bangla-copy.md`.
4. The current code you change: `web/src/app/(site)/notices/page.tsx`, `web/src/components/student/notice-bell.tsx`, `web/src/components/student/result/` (`result-page.tsx`, `results-hub.tsx`), `web/src/components/student/sitting/written-sitting.tsx`, `web/src/components/student/catalog/my-modules-page.tsx`, `web/src/lib/api/exam/notices.ts` and `sitting.ts`, and the messages `web/src/i18n/messages/{bn,en}/notices.ts`, `result.ts`, `sitting.ts`.

## Owned files

- `web/src/app/(site)/notices/**`
- `web/src/components/student/notice-bell.tsx`
- `web/src/components/student/result/**`
- `web/src/components/student/sitting/written-sitting.tsx` and the written-paper copy in `sitting.ts` messages
- `web/src/components/student/home/**`
- `web/src/components/student/catalog/my-modules-page.tsx` (this one file from the catalog folder)
- `web/src/lib/api/exam/notices.ts`, `web/src/lib/api/exam/sitting.ts`
- `web/src/i18n/messages/{bn,en}/notices.ts`, `{bn,en}/result.ts`, `{bn,en}/sitting.ts`
- `web/tests/stu-lms/**` (new)
- Your section in `ws9-gaps-and-polish.md`

Do not touch `components/student/catalog/**` (except `my-modules-page.tsx`), `components/student/purchase/**`, `lib/api/exam/catalog.ts`, `purchase.ts`, or the catalog and purchase messages. Those belong to STU-PUR.

## Items

**S-002. Notice labels.** `notices/page.tsx` (line 31 today) maps only `REMINDER`, `MCQ_RESULT` and `WRITTEN_RESULT`, and both old keys are wrong. Replace the mapping with the real kinds from plan section 6.2: `EXAM_REMINDER`, `EXAM_STARTING_SOON`, `EXAM_OPEN`, `PURCHASE_GRANTED`, `EXAM_PUBLISHED`, `RETAKE_AVAILABLE`, `ANNOUNCEMENT`, `MCQ_SCORE`, `WRITTEN_RESULT`. Labels live under `notices.kind.<KIND>` in both languages. Remove `REMINDER` and `MCQ_RESULT`. An unknown kind keeps the "other" fallback. Tests: each kind has a label in both languages, and an unknown kind falls back.

**S-004 and S-017 (UI part).** Each kind's row opens the right page: exam kinds open the exam offer (`/exam/[id]`); `PURCHASE_GRANTED` opens the purchased item (module or exam, from the notice's ids); `ANNOUNCEMENT` opens the announcement itself (no link). Copy for each kind in both languages (short sentences, the glossary terms). Tests: row links per kind.

**S-020 (UI part). Announcements.** An `ANNOUNCEMENT` row shows its title and body in the student's language, falling back to the other language when one is blank. The body can be long: show the first lines and expand. The announcement is read-only (no edit, no delete). Tests: both languages, one language blank, expand.

**S-018 (UI part). Withheld MCQ answers.** On the MCQ review (`result-page.tsx`):
- When `correct` is `null` on the options and `explanation` is `null` on a question, show the selected option and the outcome, and no correct marker. Show the copy "উত্তর এখনো প্রকাশ হয়নি। প্রশাসক উত্তর প্রকাশ করলে এখানে দেখা যাবে।" once above the review.
- When answers are released (correct is set), keep the current display.
- The outcome filter (সব / ভুল / সঠিক / উত্তর দেননি) must still work when answers are withheld: "সঠিক" and "ভুল" are then computed from `selected` and the score only if the API sends a per-question outcome. If it does not, hide the "সঠিক" and "ভুল" filter buttons and say why. Do not compute correctness on the client.
- Tests: withheld state, released state, filter behaviour in both.

**S-024 (copy and tests). Written papers and late uploads.** On `written-sitting.tsx`, one sentence on the paper page (in `sitting.ts` messages, both languages): "খাতা জমা দিতে ভুলে গেলেও শেষ সময়ে সর্বশেষ আপলোড করা খাতাটি জমা হবে। কোনো খাতা আপলোড না হলে পরীক্ষা শেষ করা যাবে না।" Add the result copy for `FAILED_TO_SUBMIT`: "পরীক্ষা শেষ করা যায়নি।" Keep the upload replace behaviour. Remove any submit-button requirement from the copy.

**ST-6 (finish the test left open).** Add the test for the deadline passing during a written upload. Use fake timers. Assert with `await expect(promise).rejects…` so the `ApiError` is caught by the assertion and not raised from the test body (the open issue in STU's report). Expected: the upload is refused with the deadline message, and the paper shows the deadline state.

**Written result states.** Test the four states on `result-page.tsx` for a written exam: awaiting (submitted, not marked), "could not finish" (`FAILED_TO_SUBMIT`), published with comment, and published with the marked PDF download. Keep the marked-PDF download (`evaluated-pdf`).

**My modules (`my-modules-page.tsx`).** Each exam row shows where the access comes from, using the `access` object (contract, plan section 8): ROSTER "রোস্টার", MODULE "মডিউল", EXAM "এই পরীক্ষা", MANUAL "প্রশাসক দিয়েছেন". Until A1 is merged, the page shows no source label and the report says so (no probe for this page). Tests for each label, and for the fallback.

**Results hub and home.** Keep the pass line and the outcome filter. Home nudges: no change unless a test shows a broken link.

**Notice bell and tab badge.** The unread count is unchanged. Check the new kinds count as unread like the others.

**Announcements in the bell.** The bell's list shows `ANNOUNCEMENT` rows like the others.

## Contract notes

- The notice view carries `kind`, `createdAt`, `readAt`, the exam or module ids where they apply, and for `ANNOUNCEMENT` the title and body in both languages (plan section 6.4). A3 and A2 send these after they merge.
- Until A2 is merged, the new kinds do not appear. Test them with mocked notices. The page must not break on a kind it does not know.
- The review shape does not change. Withheld answers arrive as `correct: null` and `explanation: null`.

## Done

- Each item has tests in `web/tests/stu-lms/**`, in Bangla and English.
- `npm run lint` (0 errors), `npx tsc --noEmit` (clean), `npm test` (single process), `npm run build` under lock `STULMS`.
- No raw colours, no `text-[..]`, no `text-sm` in your files.
- Your report section, then the ready message from the README. Name every item that waits for an API merge.

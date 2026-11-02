# Agent C brief: web (staff and student screens)

Read first: [README.md](README.md), sections 1 (D1 to D4, D10, D11, D13 to D18), 3, 4 and 6.

Goal: exam archive reads "Withdraw" everywhere it is user-facing, the structure lock is gone from the question screens, descriptions and rules are optional, the change list describes the new behaviour, and leaderboards show percent and each attempt's own maximum.

Owned files: everything under `web/src/` and `web/tests/` for exams, packages, leaderboards, results and sittings, plus both i18n files. Do not edit Java.

Code you are changing against: the API contracts in README section 4. Until Agent A and Agent B finish, code against those contracts. Do not wait for them.

## Tasks

**C1. Withdraw wording (`D1`).**
- `web/src/i18n/messages/en/staffCatalog.ts` and `bn/staffCatalog.ts`.
- Exam menu in the package list: `staffCatalog.moduleExams.menu.archive` currently reads "Archive" (the English file, around line 296). Change it to "Withdraw", and the Bangla equivalent.
- Exam status pill: add an exam-only label for status `ARCHIVED`, "Withdrawn". The shared `ARCHIVED: "Archived"` (around line 13) is used by packages and categories too, so do not change it. Use the exam-only label where exam status is shown.
- The exam editor's archive texts (`staffCatalog.editor.*`) already say Withdraw. Update `archiveBody`, which still says "An archived exam cannot be changed." (around line 558). A withdrawn exam can now be edited, so the body text must say so.
- `archivedNotice` in `staffCatalog.editor` must say the exam can be edited, and that edits are staged if it was published.
- Check the web error mapping for `EXAM_ARCHIVED` and `EXAM_SITTING_OPEN`: add a message for `EXAM_SITTING_OPEN` ("Students are sitting this exam now. Withdraw it after the sitting ends.").
- Bangla: propose "প্রত্যাহার" for Withdraw. Mark every Bangla string you wrote in your report.
- Packages and categories keep "Archive".

**C2. Editor read-only logic.** In `exam-editor.tsx`, `canEdit` currently blocks all edits for an archived exam (`role.canAuthor && !archived`). Change it so a withdrawn exam can be edited. Keep the notice. Check `exam-meta-form.tsx` (its comment says an archived exam is read-only) and update the comment and any matching logic.

**C3. Optional texts (`D18`).**
- `exam-form-state.ts`: remove the four empty-text errors in the "text" section, the `examText` checklist item, and `hasExamText`. Check `exam-editor.tsx` where `hasExamText` is used for the text section state, and set that section to done.
- `rich-text-topics.tsx`: the group shows the preview when any text is saved. It shows the editor when nothing is saved, or when there are unsaved changes. Keep Cancel and Edit. Remove the logic that depends on "complete".
- `localized-rich-text.tsx`: remove the "•" marker on an empty language tab (`dot`), and the matching behaviour.
- The text-section hint (`staffCatalog.examMeta.textHint`) says "You can save without them." Keep it, and update the wording to say the text is optional and shows to students when it is written.
- Remove the English and Bangla strings no longer used: the four `*Empty` messages and `checklist.examText`.

**C4. Package descriptions (`D18`).** In `module-details.tsx`, the package form sends `null` for an empty description (around line 238). Send `""` instead, so clearing a description clears it on the server. Check the package validation and remove any required check for the description.

**C5. Structure lock UI (`D11`).**
- `question-editor.tsx`: remove the `structureLocked` and `onStructureLocked` props and their uses (around lines 35, 44, 52, 114, 183, 211, 216). The option add and remove buttons stay visible for a published exam.
- `paste-questions.tsx`: remove `onStructureLocked` and the `QUESTION_STRUCTURE_LOCKED` branch.
- Remove the `lockedOptions` string and its Bangla equivalent.
- `errors.ts`: remove `QUESTION_STRUCTURE_LOCKED` and `EXAM_TEXT_REQUIRED`. Check that no other file still uses them.

**C6. Change list (`D10`, `D17`).** In `change-list-dialog.tsx` and its strings:
- When the list has MCQ question changes, say: "Students who already submitted keep their score. Students who are sitting now keep the questions they started with." The old re-score line must go, or read "No submitted scores change."
- When the list has a pass-percent change on a written exam, say: "Results already posted are not re-judged."
- When the list has a written maximum change: say that a maximum below the highest mark given is refused (the server does this; the copy only explains it).
- Keep the existing change rows and their formatting.

**C7. Leaderboards (`D15`).**
- Staff leaderboard (`web/src/app/(staff)/staff/exams/[examId]/leaderboard` and its components): show the `percent` column next to the score. Show the row's `maximum` next to the raw score, for example "42 / 50 (84.00%)". Show "—" when `percent` is `null`.
- Student result and leaderboard screens (`web/src/app/(site)/exam/[examId]/leaderboard`, `web/src/components/student/result`, `web/src/components/student/sitting`): show the student's own score against the maximum returned for their attempt, not against the exam's current maximum. Do not add new copy for this, unless the screen already shows "out of".
- The student exam page's full-marks fact is the exam's current maximum. Leave it, but check that it is labelled as the paper's current full marks.

**C8. Student-side wording.** Search `web/src/components/student` and `web/src/app/(site)` for user-visible "archive" text linked to exams. Report each one. Do not change the "archived exams" label for ended windows (open item O1 in the README); report it instead.

**C9. Tests (write, do not run).**
- `web/tests/s1-a/exam-form-state.test.ts`: update the test that expects `examText` not done (around line 192). The text section is always done now, and `examChecklist` no longer lists it.
- Add tests for: a blank description saved as `""`; the preview shown with one saved language; the change-list copy for MCQ and for pass percent.
- Add tests for the leaderboard percent display and the null-percent display.
- Check the existing tests in `web/tests/reviews` and `web/tests/ws3` that use empty `descriptionEn` and `rulesEn`. Update any that assume the old rule.

## Acceptance (what your report must show)

- Every user-visible exam archive string reads Withdraw (en and bn), except the O1 label and the package and category strings.
- No edit control is blocked for a withdrawn exam, except where the API refuses (such as withdraw during an open sitting).
- No text in the question screens mentions a structure lock.
- Descriptions and rules can be saved blank, and the publish checklist no longer asks for them.
- Leaderboard and result screens show percent and the attempt's own maximum.
- Every Bangla string you wrote is listed in your report.
- No file outside `web/` was edited.

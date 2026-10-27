# Evaluation API plan (written 2026-10-27)

**Scope:** the API part of the written evaluation flow for Master Admin, System Admin and Instructor. Web changes are listed at the end and are not part of this step. No Flyway migration. No new permission. No new error code.

**Decisions taken by Shahriar (2026-10-27):**

1. The marked PDF is optional on a save when the paper already has one.
2. The API keeps GET requests free of writes. The deadline job posts due sittings.
3. An assigned instructor reads the question paper only for a published written exam whose window has ended.
4. The watermark on the marked PDF is not needed. This is a web change (the student's result page), not an API change.

## 1. Optional marked PDF on save

**Where:** `LiveExamEvaluationController.evaluate` and `LiveExamEvaluationService.evaluate`.

**Change:**
- The `file` part becomes `required = false`.
- Keep the existing order of checks: the participant exists, the caller is a marker (Master Admin or assigned instructor), the sitting is settled, the status is `SUBMITTED` with an answer PDF, the marks are within 0 and the maximum.
- Then the file rule:
  - A file is sent and is not empty: validate size (80 MB) and the `%PDF` header, compress, store, and replace the previous marked PDF (as today).
  - No file, and a marked PDF is already stored: keep the stored file name, original name, size and content type. Do not touch storage.
  - No file, and nothing is stored yet: `400 VALIDATION_FAILED` with a message that a marked PDF is required the first time.
- In all cases: set marks, comment, `evaluatedBy`, `evaluatedAt`, and clear `publishedAt`. A save still returns the paper to draft.

**Current bug fixed by this:** a missing file currently reports `413 FILE_TOO_LARGE`, because `file == null` falls into the size check. The new branch replaces it.

**Comment rule:** an absent `comment` still clears the stored comment, as today. The web form always sends the text in its box, so it keeps the comment when the instructor does not change it. This is stated in the API table so the rule is not changed by accident.

**Evaluator name:** a later save with no file changes `evaluatedBy` to the saver, but the file was marked by the earlier evaluator. This follows the decision that the saver is recorded. Noted, not changed.

## 2. GET requests never write

**Where and why:** `finalizeDue` is called from three GET paths. Each one posts due sittings as a side effect of a read:
- `LiveExamEvaluationService.list` (module submissions, line 126)
- `LiveExamEvaluationService.leaderboard` (line 270)
- `LiveExamResultsService` (results report, line 88)

**Change:** remove those three calls. Keep `settleParticipant` on the POST paths (evaluate, publish, publish-results), because those are commands.

**Why this is safe:**
- `LiveExamDeadlineJob` runs every minute, is on by default in every profile (`app.exam-jobs.scheduled`, default `true`), and posts up to 500 sittings per run.
- The leaderboard opens five minutes after the window ends. A sitting's deadline is at or before the window end, so the job has posted it by then.
- For the submission list and the results report, a sitting that has just expired can show as `IN_PROGRESS` for up to about one minute. This matches the feed, which already leaves open sittings out.

**Risk:** an exam with more than about 2,500 expired sittings could take several minutes to post. Noted, not changed.

**Module submissions endpoint:** `GET /api/v1/live-exam-modules/{moduleId}/submissions` has no caller in the web app (`useModuleSubmissions` is unused). The staff screens use the paged feed `GET /api/v1/live-exam-submissions`. Keep the endpoint, make it read-only, and do not page it. Removing it is a separate decision, to be asked.

## 3. Instructor reads the question paper only after the window ends

**Where:** `LiveExamEvaluationService.questionPdf`.

**Change:**
- Master Admin and System Admin: unchanged.
- Assigned instructor: allowed only when the exam is `PUBLISHED` and its window has ended (`liveAt + durationMinutes <= now`). Otherwise `404`, the same as an unassigned instructor.
- A paper with no `liveAt` gives no window, so the instructor gets `404`.

**Why:** the instructor needs the paper to mark, and marking starts after the sitting. Before the window ends, an assigned instructor could otherwise pass the paper on before students sit it.

**Unchanged:** the marked PDF and the answer PDF checks, and the student's own question-PDF rule.

## 4. Tests

**Update:**
- `LiveEvaluationIT.theSubmissionListPostsTheDeadlineWithoutASubmitCall`: rename to say the list does not post. After the list call the missing sheet is still `IN_PROGRESS`. After `deadlineJob.settleDue()` it is `FAILED_TO_SUBMIT`.
- `LiveEvaluationIT.staffReadTheWrittenQuestionPaperOnlyWhereTheyMayMark`: the test clock starts before the window (04:00Z, window 06:00Z to 06:40Z). Add: an instructor gets `404` before the window ends, while Master Admin and System Admin get `200`. Then move the clock past 06:40Z and the instructor gets `200`.
- Any results or leaderboard test that relied on the GET posting a due sitting: add `deadlineJob.settleDue()` before the read. Found by running the suite, not guessed.

**Add:**
- Save without a file when a marked PDF is stored: `200`, the stored PDF is unchanged (download the evaluated PDF before and after, same bytes), the marks change, and `publishedAt` is cleared.
- Save without a file when nothing is stored: `400 VALIDATION_FAILED`, and nothing is written.
- Save with a file after a save without one: the new file replaces the old one.
- Save without a file by an unassigned instructor: `404`, and the stored PDF is unchanged.
- A list call does not change the status of an expired open sitting.

**Run:** `LiveEvaluationIT` and `LiveSittingIT`, one class at a time with `-DargLine=-Xmx1g` (memory is tight on this PC). Then the rest of the exams suite if memory allows.

## 5. Files

**API, changed:**
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamEvaluationController.java`
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamEvaluationService.java`
- `api/src/main/java/com/coachingcenter/api/exams/LiveExamResultsService.java`
- `api/src/test/java/com/coachingcenter/api/exams/LiveEvaluationIT.java`

**Docs, changed:**
- `docs/exam-site/current-api.md`: the evaluation table (the evaluate row, the question-PDF row, and the GET rows).
- `docs/exam-site/decisions.md`: three lines under "Written results" (the optional PDF, the no-write GET rule, the instructor paper rule).

**Not changed:** `LiveExamSittingService`, `LiveExamDeadlineJob`, `ErrorCode`, `Permissions`, and any migration.

## 6. Web follow-ups (after the API)

- `web/src/components/staff/marking/submission-form.tsx`: the file is optional when the paper already has a marked PDF (`paper.evaluatedPdf`). The "file required" error only applies when nothing is stored. The helper text says a new file is optional.
- `web/src/lib/api/exam/staff-marking.ts`: update the comment that says the server requires a new file on every save. Make the file part optional in `useSaveMarking`.
- `web/src/components/student/result/` (the marked-PDF view, B-03): remove the watermark from the student's marked PDF.
- The staff list, feed, and results screens need no change, because they already read the paged feed.

## 7. Open questions

- Confirm that a sitting's status `IN_PROGRESS` on the staff list for up to one minute after the deadline is acceptable. The alternative is to filter open sittings out of the module list, as the feed does. I recommend the filter for the module list only if the endpoint is kept as a UI-facing list. Since no UI calls it, I have not made that change.
- Confirm that removing the module list endpoint is a separate decision, not part of this plan.

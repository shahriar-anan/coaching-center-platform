# Launch prompt: A2 API-NOTICE (phase 2)

You are agent **A2 API-NOTICE**. You build the student notice kinds and the one-hour reminder. Your branch is `ui/api-notice`.

## Setup

1. Create your worktree from `catalog-acquisition` (which has V39 merged):
   `git -C F:/coaching-center-platform worktree add F:/cc-wt/api-notice -b ui/api-notice catalog-acquisition`
2. Work only inside `F:\cc-wt\api-notice`. Never write under `F:\coaching-center-platform\` except the lock file.
3. Copy `F:\cc-wt\w9-api\api\.env` into `F:\cc-wt\api-notice\api\.env`. Test database keys only; never print values.

## Read first

- `docs/exam-site/plans/ui-delivery/student-purchase-lms-plan.md`: sections 2 (decisions D-d6, D2), 4 (S-002, S-004, S-016, S-017), 6.2 (the notice kinds table), 8 (contract shapes), 9.2 (file ownership).
- `docs/exam-site/plans/ui-delivery/seed-data-plan.md`, section 6.3 only (what the seed expects).
- `docs/exam-site/plans/api-completion/README.md`.

## Rules

- Never edit Flyway `V1` to `V39`. You do not write a migration.
- You own `StudentNoticeWriter`, `StudentInboxService` and `StudentReminderJob`. Do not edit `LiveExamPublicService`, the access service, or `LiveExamAccess`.
- You add the notice hook in `LiveExamOrderTx` for `PURCHASE_GRANTED`. A4 (bundle order) also changes that file later, so keep the change small and in one place.
- API-ADMIN's manual grant service will call your `PURCHASE_GRANTED` method after you merge. Do not edit API-ADMIN's files. Expose the method so that call is one line.
- The in-website 48-hour `EXAM_REMINDER` stays exactly as it is. Do not change its lead time.
- The hour-before **mail** stays off. Do not touch `ExamMailService`. Only the in-app 1-hour notice is new.
- Maven runs only while `F:\coaching-center-platform\.heavy.lock` holds `API`. Write `API` to the file. Use the full path to the wrapper: `& 'F:\cc-wt\api-notice\api\mvnw.cmd' -B clean verify -Dtest=<ClassName>`. Integration tests run through Surefire, so pass `-Dtest`. If another agent holds the lock, wait; never delete another agent's lock. Delete yours when the run ends.
- Add every new route to `AuditLogIT` (you add none unless you need one).
- Document every kind and shape under "Added for the phase 2 UI (API-NOTICE)" in `docs/exam-site/current-api.md`.
- One commit per item ID. The message starts with the ID and ends with the attribution line.

## Items, in order

1. **One-hour reminder (S-017, D-d6).** A new scan in `StudentReminderJob`, separate from the 48-hour one: each published exam starting within 60 minutes, for each student with access. Kind `EXAM_STARTING_SOON`, message key `studentId:examId:liveAtMillis`, so it is written once per student, exam and start time. The 48-hour `EXAM_REMINDER` scan is unchanged. Test: fires once; a second scan adds nothing; a student without access gets nothing; an exam starting in 61 minutes gets nothing yet.
2. **Exam open (S-004).** Kind `EXAM_OPEN`, written once per student with access when the exam's window opens. Keyed by student, exam and start time. Test: once, not before the window, not for non-owners.
3. **Purchase granted (S-004).** Kind `PURCHASE_GRANTED`, written when a paid order creates its grant, in `LiveExamOrderTx`. Expose `StudentNoticeWriter.purchaseGranted(studentId, moduleOrExamId, scope)` so API-ADMIN's manual grant can call it later. Test: once per order; no notice when the grant is refused.
4. **Exam published (S-004).** Kind `EXAM_PUBLISHED`, written to students with access to the module when one of its exams is published (the publish path in `LiveExamService`). Test: owners get it; non-owners do not; publishing twice writes once.
5. **Retake available (optional, S-004).** Kind `RETAKE_AVAILABLE`, only if the retake rule already exists in the sitting service without new logic. If it needs new rules, skip it and say so in your report.
6. **Labels for the inbox (S-004).** `StudentInboxService` returns each kind with its exam or module ids, so the page can link. `ANNOUNCEMENT` rows are written by API-ADMIN's class; you only list the kind in the inbox view. Test each kind's row shape.

## Checks

- The touched `*IT` classes green (`-Dtest=<Class>`).
- Full suite from clean, alone, under the lock. Only `BookOrderIT` and `CoursePurchaseIT` may fail.
- `current-api.md` is updated.

## Done

1. Fill in "Report — API-NOTICE" at the end of `docs/exam-site/plans/ui-delivery/ws9-gaps-and-polish.md` on your branch.
2. Send:

```
READY TO MERGE: ui/api-notice (agent A2 API-NOTICE)
Last commit: <hash>
Checks: touched *IT <n green>; full suite <n run, failures list>
Items done: <IDs> | partly: <IDs and what is left> | skipped: <IDs and why>
Contract changes: <list>
Notes for API-ADMIN and A4: <the purchaseGranted call and the LiveExamOrderTx hook>
```

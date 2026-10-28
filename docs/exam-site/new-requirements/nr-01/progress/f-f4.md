# F4: keep today's behaviour on the new schema (agent F)

## What changed

Behaviour is meant to be unchanged for every existing route. The new schema is wired in underneath.

**Membership in place of `exam.getModuleId()`**
- New helper `LiveExamMemberships` (`@Component`): `primaryModuleId(examId)` (first active package by position), `requirePrimaryModuleId(examId)` (404 when the exam is in none), `activeModuleIds`, `attach(moduleId, examId, now)`. Not in contracts §4; it is the one place that reads or writes an exam's packages. Reason: about 40 call sites need it. Until B, an exam has one active membership, so "primary" is the only package.
- `LiveExamRepository`: the "by module" queries keep their names and signatures and now join the active membership (`LiveExamPackageExam`, `removed_at is null`), not `e.moduleId`. Covered: `findByModuleIdAndDeletedAtIsNull`, both `findByModuleIdAndStatus…`, `findAccessibleToStudent`, `findVisibleRoutineCandidates`, `findDatedPublishedByModuleId`, `findUpcomingPublished`, `countPublishedByModuleAndType`, `countByModuleAndType`. Each keeps its existing grant and roster semantics, including the existing "module grant ignores revoked" behaviour.
- Services moved to the primary package: `LiveExamService`, `LiveExamEvaluationService`, `LiveExamResultsService`, `LiveExamPublicService`, `LiveExamOrderService` (exam purchase), `LiveExamGrantAdminService` (exam grant's package), `StudentOwnAccessService` (through its `Viewer`), `StudentReminderJob`, `ExamMailQueue`.

**Access (`LiveExamAccess`, contracts §4)**
- `state(studentId, exam, now)` returns an `AccessState` (new file). `canSit`, `hasEver`, `hasModule` as the contract says; `hasExam` kept as `@Deprecated`, delegating to `state(...).granted()`.
- Grants come from roster rows, module grants on the exam's packages, and exam grants. R4 applies: a removed membership still covers a grant made before the removal. The end of access comes from `AccessWindow`; `accessDays` is null for every existing exam, so the end stays null and nothing expires, as the plan requires.
- Two new lookups: `LiveExamGrantRepository.findByStudentIdAndScopeAndModuleId` / `…AndExamId` (targeted, not `findAll`) and `existsByStudentIdAndExamId`, `existsByStudentIdAndModuleId`; `LiveExamParticipantRepository.existsByExamIdAndStudentIdAndDeletedAtIsNull`.

**Create, update, copy (`LiveExamService`)**
- `create`: sets `categoryId` (the package's category), `instructorId` (the package default, R3), `mode = LIVE`, `answersChangeable = true` (P-c), `endsAt = liveAt + duration`; runs `ExamWindow.validate`; attaches the membership.
- `update`: after schedule changes, recomputes `endsAt` and validates. The lock code (`CONTENT_LOCKED`, the window-clash check) is untouched, as the plan says A removes it.
- `copy`: copies category, instructor, mode, `answersChangeable`, `negativeMarks`, each question's `multipleAnswer`; writes paper version 1 when the source has a PDF; attaches the membership to the source's primary package.
- `publish`: sets `publishedAt` on the first publish only; notice recipients use the primary package.
- `uploadQuestionPdf`: creates the next paper version (`max + 1`) and points `questionPaperId` at it. The legacy `question_*` columns are still written.
- `end(exam)` now returns the stored `endsAt`; `windowEnd(exam)` computes it.

**Sittings (`LiveExamSittingService`)**
- `latest(examId, studentId)` replaces the one-row `findByExamIdAndStudentIdAndDeletedAtIsNull` calls (it uses `…OrderByAttemptNoDesc`).
- `start` sets `attemptNo = 1` and `questionPaperId` (from the exam's current paper).
- `retake` (same rule as today) creates kind `ARCHIVED`, attempt 1. Its deadline is unchanged.
- MCQ scoring uses `McqScoring.total` and `McqScoring.passed`; the answer key is built from the database (`key(examId)`). With no negative marks and single answers, the numbers are the same as before.
- `attemptKind` is added beside the legacy `kind` in `SittingView`, `TakeView`, `ReviewedResult`. Legacy `kind` comes from `LiveExamParticipant.legacyKind` (`LIVE` for LIVE, `RETAKE` for every other attempt).
- Removed the now-unused `correctOptions`, `marks` and `passed(int, …)`.

**Legacy kind in other views** (contracts §7.7)
- `LiveExamResultsService`: `Row` and `HistoryRow` get `attemptKind`; `kind` is legacy. The kind filter compares the legacy value. The "practice" count counts legacy `RETAKE`.
- `LiveExamEvaluationService`: `SubmissionView` gets `attemptKind`.
- `LiveExamSubmissionFeedService`: `FeedRow` gets `attemptKind`.
- `LiveExamParticipationService`: the "retakes" count is `kind in ('ARCHIVED','RETAKE')`, since today's late attempts are now `ARCHIVED`.

**Schema-facing**
- `ErrorCode`: the eight codes are already in (F3).
- `Permissions` and `LiveExamPermissionContributor`: `LIVE_EXAM_POPULAR` (Master Admin), `LIVE_EXAM_RETAKE_GRANT` (Master Admin, System Admin), `LIVE_EXAM_PACKAGE_EDIT` (Master Admin, System Admin), `LIVE_EXAM_FAVOURITE` (Student).
- `AuditRecorder`: the contract's audit constants, except `LIVE_EXAM_INSTRUCTOR_UNASSIGN` (which already existed). Nothing records them yet.
- `StudentNoticeWriter`: `examUpdated(examId, studentIds)` (kind `EXAM_UPDATED`) and `retakeAvailable(examId, studentId)` (kind `RETAKE_AVAILABLE`). Keys include the clock instant. Nothing calls them yet.

**Demo seed** (`DemoContentSeed`)
- `INSERT_EXAM` sets `category_id` (from the module), `mode = 'LIVE'`, `ends_at`, `published_at`. After it, `exam()` inserts the membership (id prefix `1`), sets `instructor_id` from the module's instructor, and for a written exam with a PDF inserts paper version 1 (id prefix `2`) and sets `question_paper_id`.
- `demo-cleanup.sql` (`api/.cursor/progress/ui-delivery/`) deletes the new rows in dependency order: answer confirmations, retake grants, favourites, change log, revisions; then clears `question_paper_id`, deletes paper versions and memberships, then the exams. Without this, staging's cleanup would fail on the foreign keys.

## Tests

**Run 1, the live-exam batch** (`Live*IT,Live*Test,DemoContentSeed*,StudentAccessIT,ExamAccessGateIT,ExamAccessModeIT,PublicAccessIT,BundleOrderIT,ExamMailIT,ExamPurchaseIT,ExamModuleIT,ExamTreeIT,ExamResultsIT,ExamAnalyticsIT,ExamWrittenSubmissionIT,ExamMcqAttemptIT,ExamQuestionIT,ExamComponentIT,ModuleCoverIT,Nr01MigrationIT,McqRescorerIT,ExamWindowTest,ExamPricingTest,McqScoringTest,AccessWindowTest,SystemStatusIT`): 27 classes. Every class passed except three, all because of test fixtures, not behaviour:
- `ExamMailIT` (13 errors): raw SQL inserts into `live_exams` without `category_id` and `mode`, and without `ends_at` (the window check needs it). Also a raw `update live_exams set live_at` that did not move `ends_at`. Fixed by adding those columns and inserting the membership row for each raw exam. No assertion changed.
- `LiveEvaluationIT` (1 error): raw insert without `category_id`, `mode`, `ends_at`. Fixed the same way. No assertion changed.
- `DemoContentSeedIT` (1 error): `removeTopUp` deleted exams without first removing memberships and paper versions. Fixed the delete order. No assertion changed.

**Run 2, the three fixed classes plus the new notice IT**: `DemoContentSeedIT` 15/15, `LiveEvaluationIT` 63/63, `StudentNoticeWriterNr01IT` 2/2 (new). `ExamMailIT` had 9 errors (the second-exam fixture still lacked its column list), then 1 (a window move that did not update `ends_at`), then passed 13/13 after the last two fixture fixes.

**Full suite**: see [f-done.md](f-done.md). It is the final run, on a fresh database, with real counts.

## Deviations from contracts.md

- **`LiveExamMemberships`** is a new helper (not in §4), described above.
- **Repository queries keep their names** and now join memberships. Stage 2 should read them as "exams in this package via membership", not "exams whose `module_id` is this".
- **Attempt kind in staff rows**: contracts §7.7 says new staff rows use `attemptKind` only. Existing staff rows keep the legacy `kind` as well and add `attemptKind`, so the current web keeps working. Question for the orchestrator: confirm this for stage 2.
- **`LiveExamAccess.hasExam`** is kept, deprecated, delegating to `state(…, clock.instant()).granted()` (it now takes a `Clock`). Its callers: `LiveExamService.get`, `ExamMailTexts`.
- **`AccessState.NONE`** is a constant on the record (no contract text).
- **`latest()`** in the sitting service orders by attempt number and takes the first. It keeps today's single-row behaviour.
- **Take deadline unchanged**: a live sitting's deadline is still the shared window end, as today. D changes it to start plus duration (D15).
- **Write-side paper creation**: `createdBy` on paper versions is null. The service methods do not receive the acting user; A should pass it.
- **Copy copies only the primary membership**. Contract §6 (A) says memberships are copied too, which is A's change.
- **Old window-conflict and lock code is kept** (`rejectWindowConflict`, `CONTENT_LOCKED` raises), as the plan says.
- **The seed's membership and paper ids** use hex prefixes `1` and `2` (both free in the seed's id scheme).
- **`demo-cleanup.sql` changed**: it is outside `api/src`, but it is part of staging's demo tooling and must know the new tables.

## For you to click

None: this slice has no UI.

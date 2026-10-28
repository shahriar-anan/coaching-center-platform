# F done: hand-over from stage 1 (agent F)

Branch `nr01/f-foundation`, commits `ef17b52` (F1–F2), `000aaf0` (F3), `cfa4a53` (F4), `a79d324` (F5). Slice notes: [f-f1.md](f-f1.md), [f-f2.md](f-f2.md), [f-f3.md](f-f3.md), [f-f4.md](f-f4.md).

## 1. Full suite (the final run)

Command: `powershell -NoProfile -File docs/exam-site/new-requirements/nr-01/tools/nr01-mvn.ps1 -Agent F -Full -ResetDb`, run in the background on a freshly created `cc_test_nr01_f`.

```
EXIT 0
Test classes (Surefire reports from this run, 17:40–17:43): 98
Tests: 452    Failures: 0    Errors: 0
```

Every report file is from this run (no stale reports). The count includes the root-package `FoundationIT` (3) and `SchemaIT` (5).

## 2. Where the code differs from contracts.md

Also listed in the slice notes. The orchestrator should fold these into `contracts.md` before stage 2.

| # | Where | Contract says | Code does | Why |
|---|---|---|---|---|
| 1 | §4 helpers | helpers as listed | adds `LiveExamMemberships` (`@Component`): `primaryModuleId`, `requirePrimaryModuleId`, `activeModuleIds`, `attach` | About 40 call sites read an exam's package; one helper keeps them consistent. |
| 2 | §4 `ExamPricing.validate` | `VALIDATION_FAILED` with `fieldErrors` | throws `ApiException` whose `details` is a `List<FieldErrorDetail>` | `ApiExceptionHandler` writes an `ApiException`'s `details` only into the `details` field; `fieldErrors` stays null. See question 1. |
| 3 | §4 `LiveExamAccess` | `hasExam` delegates to `canSit` | `hasExam` is `@Deprecated`, delegating to `state(…, clock.instant()).granted()` | The constructor now takes a `Clock`; with no access days set, the result is the same. |
| 4 | §4 `AccessState` | (no constant) | `AccessState.NONE` constant added | Convenience. |
| 5 | §3 repositories | `LiveExamRepository` unchanged | "by module" queries keep their names and join the active membership | Callers unchanged; see §3 of this note for the new meaning. |
| 6 | §3 entities | all new entities extend nothing special | tables without `deleted_at` don't extend `BaseEntity`; they declare `@GeneratedValue(strategy = GenerationType.UUID)` ids | `BaseEntity` maps `deleted_at`. |
| 7 | §3 `LiveExamPackageExamRepository.existsActive` | repository method | a `default` method delegating to a derived query | Same behaviour. |
| 8 | §3 `LiveExamRetakeGrantRepository.findActive` | repository method | a `@Query` (`revokedAt is null`) | Clearer than a derived name. |
| 9 | §3 `score` | `BigDecimal` everywhere | done, and views print whole marks as integers through `McqScoring.plain` (`12`, not `12.00`) | Keeps today's JSON. |
| 10 | §5 `ErrorCode` | eight new codes | added (F3). Only `EXAM_WINDOW_INVALID` is raised so far. | `ExamWindow` raises it. |
| 11 | §6 `ExamView.moduleId` | (not stated) | the exam's primary package, from membership | Keeps the field meaningful for an exam with one package. |
| 12 | §6 `take`/`retake` | take kind and deadline from `ExamWindow` | take still uses the shared window end as the deadline; retake creates `ARCHIVED`, attempt 1 | Behaviour must not change in F; D changes the deadline (D15). |
| 13 | §6 `copy` | copies memberships (A) | copies the primary membership only; copies category, instructor, mode, `answersChangeable`, `negativeMarks`, `multipleAnswer`, and a version-1 paper | A owns the full copy. |
| 14 | §6 staff rows | "staff rows use `attemptKind` only" | staff rows keep legacy `kind` and add `attemptKind` | The current staff web reads `kind`. Question 2. |
| 15 | §6 paper versions | paper written by F | `createdBy` on paper versions is null | The service methods do not receive the acting user. A should pass it. |
| 16 | §4 `McqRescorer` | `rescore(examId)` | `@Transactional`; returns changed-score count; sets `passed` too | Contract said so. Nothing calls it yet. |
| 17 | §3 `LiveExamAnswerConfirmation` | (entity with composite key) | `@IdClass` with a nested `Key`, same as `LiveExamAnswer` | Consistency. |
| 18 | §5 `AuditRecorder` | new constants | added, except `LIVE_EXAM_INSTRUCTOR_UNASSIGN` which already existed | Nothing records them yet. |
| 19 | §5 `StudentNoticeWriter` | `examUpdated(examId, studentIds)` | same signature; key includes the clock instant | No grant or publish id exists in the signature. Nothing calls it yet. |
| 20 | §5 `StudentNoticeWriter.retakeAvailable` | `retakeAvailable(examId, studentId)` | same; key is student, exam and clock instant | Same reason as 19. |
| 21 | Demo seed | (F4) | `category_id`, `mode = 'LIVE'`, `ends_at`, `published_at`, membership id prefix `1`, paper id prefix `2`, `instructor_id` from the module | Seeded exams need membership or they vanish from listings. |
| 22 | Demo cleanup | (not in contracts) | `api/.cursor/progress/ui-delivery/demo-cleanup.sql` deletes the new tables in dependency order | Without this, staging's cleanup fails on foreign keys. |
| 23 | `legacyKind` | (§7.7) | `LiveExamParticipant.legacyKind(kind)`: `LIVE` for LIVE, `RETAKE` for all else | One place for the mapping. |
| 24 | `LiveExamParticipationService` retake count | (not stated) | counts `kind in ('ARCHIVED','RETAKE')` | Today's late attempts are now `ARCHIVED`; the count must not drop. |

## 3. Remaining reads, for stage 2

### 3a. `getModuleId()` reads left in the live-exam area

After F4, no code reads an **exam's** own `moduleId` (`LiveExam.getModuleId()`). Every exam read now goes through the membership table (see 3b). The remaining `getModuleId()` calls are on other entities, and each owner needs to know about them:

| File:line | Entity | Owner |
|---|---|---|
| `ExamMailQueue.java:82` | `LiveExamOrder` (the order's package) | C (purchase mail) |
| `LiveExamGrantAdminService.java:137` | `LiveExamGrant` (grant view) | C |
| `LiveExamOrderService.java:279,280,367,386` | `LiveExamOrder` rows | C |
| `LiveExamOrderTx.java:190,199,206,222` | `LiveExamOrder` / grant | C |
| `LiveExamStaffModuleService.java:139` | `LiveExamInstructor` (package default instructor) | B (package instructor) |
| `LiveExamAccess.java:84` | `LiveExamPackageExam` (membership) | (F, intentional) |
| `DemoContentSeed.java` (insert) | writes `live_exams.module_id` as "created in" | orchestrator |

### 3b. Reads through the **primary** membership (`primaryModuleId` / `requirePrimaryModuleId`)

These read "the exam's package". They are correct for one membership per exam. **When B adds several packages per exam, each of these must be decided: the first package, the package the caller is in, or all of them.** The count is 37 call sites:

- `LiveExamService.java` (8): `copy` (source package), `publish` (window conflict and recipients), `rejectWindowConflict`, `view` (`ExamView.moduleId`), `routineView`, `visibleModule`, and `primaryModuleId` itself.
- `LiveExamEvaluationService.java` (10): marking and reader checks (`requireMarker`, `requireReader`, `readerOf`), the module lookup at `modules.findById(primaryModuleId(exam))`.
- `LiveExamResultsService.java` (5): instructor scope (`assigned`), module lookup for history rows.
- `LiveExamPublicService.java` (4): public listing and upcoming exams.
- `LiveExamGrantAdminService.java` (2): exam grant's package.
- `LiveExamOrderService.java` (1): exam purchase's package (`publishedModule`).
- `StudentOwnAccessService.java` (1): `Viewer.exam`.
- `StudentReminderJob.java` (2) and `ExamMailQueue.java` (4): reminder and publish recipients (`findActiveStudentsWithAccess`).

### 3c. Legacy `question_*` columns, still read

These should move to the paper version the participant sat (E29). For a sitting that already exists, the paper is `participant.questionPaperId`. The columns are still mirrored on upload and copy, so nothing breaks until `V52`.

| File:line | What it reads | Owner |
|---|---|---|
| `LiveExamEvaluationService.java:425` | `exam.getQuestionStoredFileName()` (evaluator's PDF check) | D |
| `LiveExamEvaluationService.java:432` | same (evaluator's PDF bytes) | D |
| `LiveExamSittingService.java:98` | `exam.getQuestionStoredFileName()` (student's question PDF flag) | D |
| `LiveExamSittingService.java:270,274` | same (student's question PDF bytes, `questionPdf`) | D |
| `LiveExamService.java:301–317` | copy: reads source file name, original name, content type | A |
| `LiveExamService.java:362` | upload: previous stored name for `commitReplacement` | A |
| `LiveExamService.java:414` | publish: written exam needs a PDF | A |
| `LiveExamService.java:620` | `questionPdf` on the staff view | A |
| `LiveExamService.java:718–721` | paper version creation (reads the exam's file fields) | A |

Writes of the legacy columns (for the same reason): `LiveExamService` upload and copy; `LiveExamSittingService` is read-only for them.

## 4. Existing `*IT` classes in the exam area, with owners

Owner is taken from the workstream file that names the class. Where no file names it, the owner is the one whose code it exercises, marked *(by code)*. Where neither applies, it is *unassigned*.

| Test class | Owner | Basis |
|---|---|---|
| `LiveExamIT` | A | named in a-authoring |
| `LiveExamArchiveIT` | A | archive is in `LiveExamService` (A) |
| `ExamQuestionIT` | A | question editing (A) *(by code)* |
| `LiveCatalogIT` | B | named in b-packages |
| `LivePublicCatalogIT` | B | named in b-packages |
| `ModuleCoverIT` | B | `ModuleCoverService` (B) *(by code)* |
| `PublicAccessIT` | B | public catalog reads (B) *(by code)* |
| `LiveEvaluationIT` | D | named in d-sitting |
| `LiveSittingIT` | D | named in d-sitting |
| `LiveExamStudentStatusTest` | D | named in d-sitting |
| `BundleOrderIT` | C | named in c-commerce |
| `ExamAccessGateIT` | C | named in c-commerce |
| `ExamPurchaseIT` | C | named in c-commerce |
| `LiveExamPurchaseIT` | C | named in c-commerce |
| `LiveGrantAdminIT` | C | named in c-commerce |
| `LiveRosterIT` | C | named in c-commerce |
| `StudentAccessIT` | C | named in c-commerce |
| `LiveExamPaymentRecheckTest` | C | `LiveExamPaymentRecheck` (C) *(by code)* |
| `ExamAccessModeIT` | C | access (C) *(by code)* |
| `ExamMailIT` | orchestrator (split) | publish, reminder and purchase mail; purchase mail is C's *(by code)* |
| `ExamMailHourGateTest` | orchestrator | mail hour gate, not owned by an agent |
| `DemoContentSeedIT` | orchestrator | integration.md |
| `DemoContentSeedGuardTest` | orchestrator | seed guard |
| `SystemStatusIT` | orchestrator | not in any NR-01 file |
| `AuditLogIT` (`common/audit`) | orchestrator | integration.md (`API_OPERATIONS` conflicts) |
| `ExamAnalyticsIT` | unassigned | not named; check whether it covers live results (D) |
| `ExamResultsIT` | unassigned | not named; check whether it covers live results (D) |
| `ExamModuleIT` | unassigned | not named; check whether it covers live packages (B) |
| `ExamTreeIT` | unassigned | not named; likely the legacy exam tree |
| `ExamComponentIT`, `ExamMcqAttemptIT`, `ExamWrittenSubmissionIT`, `QuestionBankIT` | none (legacy) | course-side exam components; outside NR-01 |
| `SslCommerzGatewayTest`, `SslCommerzProfileGuardTest`, `SslCommerzPropertiesTest` | none | payment gateway; out of scope |
| `SystemStatusServiceTest` | none | out of scope |

New tests from F (owner F): `Nr01MigrationIT`, `McqRescorerIT`, `StudentNoticeWriterNr01IT`, `ExamWindowTest`, `ExamPricingTest`, `McqScoringTest`, `AccessWindowTest`.

Non-exam classes (courses, books, auth, foundation, schema) have no NR-01 owner. All of them ran and passed in the full suite.

## 5. Test files changed outside F's new tests

No assertion was changed. Changes are fixtures and one cleanup order:

- `ExamMailIT`: raw `live_exams` inserts now set `category_id` (from the module), `mode`, `ends_at`, and insert a membership row; one raw `live_at` update also moves `ends_at`. Owner: orchestrator (the class is split, see §4).
- `LiveEvaluationIT`: one raw insert, same fix. Owner: D.
- `DemoContentSeedIT`: `removeTopUp` clears paper and membership rows before deleting an exam. Owner: orchestrator.

## 6. Questions for the orchestrator

1. **Field errors.** `ExamPricing.validate` (and any stage-2 validation) needs `fieldErrors` in the error body. `ApiExceptionHandler` writes only `details` for an `ApiException`. Should a list-valued `details` go into `fieldErrors`? It is shared code, so I did not change it.
2. **Staff rows.** Contracts §7.7 says new staff rows use `attemptKind` only. I kept legacy `kind` on existing staff rows and added `attemptKind`. Confirm this is what you want for stage 2.
3. **Copy memberships.** Who owns copying all memberships (A's `copy`)? I copied the primary one only.
4. **Paper author.** The paper-version `createdBy` is null because the services do not receive the actor. A should pass it; say if you want that in F's contract instead.
5. **Notice keys.** `examUpdated` and `retakeAvailable` key on the clock instant, since the contract signatures carry no publish or grant id. A may prefer to pass one.
6. **Demo cleanup script** changed outside `api/src` (`api/.cursor/progress/ui-delivery/demo-cleanup.sql`). Confirm this is in scope for stage 3.
7. **`gen_random_uuid()`** needs PostgreSQL 13+. The local server is 17; the staging version is not checked here.

## 7. Anything skipped

- Nothing in the F slices was skipped.
- Not run: any web checks (out of scope for F).
- Not done by design: no push, no deploy, no staging or client-vps access.

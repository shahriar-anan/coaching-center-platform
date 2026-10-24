# Phase 1 — Backend runs and database

Date: 2026-10-22 | Branch: `catalog-acquisition` @ `2740e5d` | Mode: inspect, plus the build and the `Live*IT` run Shahriar approved

Checklist: brief sections 5.2 and 5.5. Endpoint scoring is Phase 2–4. VPS was not contacted.

The Rules Extract below replaces the one in `audit/phase-0.md`. Later exam checks use this extract.

## Precedence

1. `docs/phases/phase-3/phase-3-scope.md` (confirmed 2026-10-19, file written 2026-10-20 03:40). Sections 1–12.
2. The five live stage plans, test lists, and rules under `api/.cursor/plans|tests|rules/api-live-*`, plus `api/.cursor/rules/api-exam-invariants.mdc` (2026-10-20 03:40). They match the scope. They do not add a conflicting rule.
3. What this phase ran. `docs/phases/phase-3/phase-3-status.md` (2026-10-20 03:40) says the live exam API is complete, including slice 2. That claim is tested below, not taken from the file.
4. `docs/phases/README.md` (2026-10-20 01:48) still says slice 2 is open. The status file is newer. This is a doc drift, not an open product rule.

The PRD, the brief, and `api/.cursor/plans/api-exam-*.md` are not sources. The old plan, rule, and test files are stubs that point at Phase 3. `V14`–`V23` and the `Exam*Controller` classes are the replaced model. They are still in the tree.

No open product decision turned up in scope sections 1–12. Access for this delivery is `MANUAL` only. `OPEN`, `ENROLLED_ONLY`, and `PURCHASE_REQUIRED` stay for a later LMS.

## Rules Extract

Source for every row is `docs/phases/phase-3/phase-3-scope.md` unless a stage file is named. "Backend" means this run on 2026-10-22, not the status file.

| Topic | MCQ | Written | Backend this run |
|---|---|---|---|
| Types, states, lifecycle | One exam is `MCQ` or `WRITTEN`. Draft on each change, final save, then publish. Students see published exams only. The window end does not archive the exam. Archive is a separate action on a module or a category branch. Nothing is deleted. §§3–4 | Same lifecycle. Question paper is one PDF. Maximum is the number on the exam. §4 | **VERIFIED.** `LiveExamIT` (6) and `LiveCatalogIT` (7) passed, including both types on one module, draft until final save, and the exam staying published after the window. Schema checks on `coaching_center_test`: exam type `MCQ`/`WRITTEN`, exam status `DRAFT`/`PUBLISHED`, module status `DRAFT`/`PUBLISHED`/`ARCHIVED`. |
| Eligibility and attempts | Roster is `MANUAL`. Only a Master Admin marks students, by email, phone, name, or a paste of email and phone. A System Admin receives 403. A marked student sees published exams, including one published later. One retake, only if the student never tapped Take exam during the live window. A live participant does not get it. No second retake. The retake is off the live leaderboard. §§5–6 | Same roster and the same one retake. A written retake is marked and stays off the live board. §§6–7 | **VERIFIED** for Master-Admin-only mark, email/phone/name/paste, and the one retake (`LiveRosterIT` 4 passing cases, `LiveSittingIT.aStudentWhoNeverTappedCanRetakeOnceAndALiveParticipantCannot`, `LiveEvaluationIT.aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard`). **NOT RUN:** `markedStudentSeesPublishedExamsIncludingOnePublishedLater` failed before its assertion. See the test result below. |
| Window, duration, deadline | One live instant plus a duration. Store an absolute time. Show it in `Asia/Dhaka`. The server clock sets start and end. A late tap keeps the shared end. Five clear minutes between exams in the same module. Another module does not block. Publish is rejected when the end is not still ahead of the server clock. §4.1 | Same window rules. | **VERIFIED.** `LiveExamIT.fiveMinuteGapIsEnforcedInsideOneModuleOnly`, `LiveExamIT.anEndedWindowCannotBePublished`, `LiveSittingIT.readingBeforeTakeHidesStemsAndALateTapKeepsTheSharedEnd`. |
| Timer and auto-post | The deadline posts saved answers without a student submit call. Lazy, on the next read of the exam, result, submission list, or leaderboard. §6.1 | The deadline locks the sheet. An upload still transferring does not count. No stored PDF is failed to submit, score zero. §6.2 | **VERIFIED.** `LiveSittingIT.aReadAfterTheDeadlinePostsAnMcqThatWasNeverSubmitted`, `aReadAfterTheWrittenDeadlinePostsAStoredPdfAndAMissingPdf`, `LiveEvaluationIT.theSubmissionListPostsTheDeadlineWithoutASubmitCall`, `openingOrMarkingADuePaperPostsItWithoutTheSubmissionList`. |
| Before Take exam | Name, description, rules, start, and end. Stems, options, and the question PDF stay hidden. Opening the card does not create a participant. §§4.1, 6 | Same, and the question PDF stays hidden until Take exam. | **VERIFIED.** `LiveExamIT.mcqMaximumIsTheSumAndTheStudentUpcomingPayloadHidesStems`, `LiveSittingIT.readingBeforeTakeHidesStemsAndALateTapKeepsTheSharedEnd`. |
| In the sitting | Save replaces the full selection. An omitted question is cleared. An early submit returns the score and does not freeze answers. The chosen option, the correct option, and the explanation stay hidden until that sitting's deadline. No shuffle. §4.2, §6.1 | Another upload replaces the PDF. The server keeps the latest fully stored file. After the deadline the sheet is locked. An early submit does not score or lock. §6.2 | **VERIFIED** for save, early submit, review hidden until the deadline, and PDF replace: the matching `LiveSittingIT` cases passed. **INFERRED** for "do not shuffle": author `position` is stored; this run did not assert order. |
| Scoring | Maximum is the sum of question marks. Pass when `score × 100` is at least `pass percent × maximum`. Pass percent is 0 through 100 and is required before publish. A blank paper fails even when the pass percent is 0. A paper with at least one saved answer uses the formula. No negative mark, no partial mark, no second correct option. §§4, 4.2 | Maximum is the number on the exam. The same pass formula after a mark is saved. A no-submit zero fails. §4, §7.2 | **VERIFIED** for the pass formula and the blank-MCQ failure: `LiveSittingIT.aBlankMcqPostsZeroAndIsNotFailedToSubmit`, `aBlankMcqFailsWhenThePassPercentIsZero`, `LiveExamIT.publishRequiresPassPercentQuestionsAndAWrittenPaper`. **VERIFIED** one correct option: unique index `live_exam_options_one_correct_idx` is present on the test database. **INFERRED** no negative or partial column: the live tables have none. |
| Submission statuses | `IN_PROGRESS`, then `SUBMITTED`. MCQ has no failed-to-submit status. A blank paper is submitted as zero. §6.1 | `IN_PROGRESS`, `SUBMITTED`, or `FAILED_TO_SUBMIT`. §6.2 | **VERIFIED** by the sitting tests above and by the check constraint on `live_exam_participants.status`. |
| Upload | n/a. An MCQ exam has no PDF. | One compressed PDF, 80 MB ceiling, latest file only. A larger file is rejected and nothing is stored. Not images. §6.2 | **VERIFIED.** `LiveSittingIT.aWrittenPdfReplacesThePreviousFileAndAMissingPdfIsFailedToSubmit` passed. `LiveExamSittingService.MAX_PDF_BYTES` is 80 MB, and that test posts a part whose size is one byte over the ceiling. |
| Evaluation | Automatic on post. | One instructor per module, created like a System Admin (no password, activation code). That instructor or the Master Admin saves marks from 0 through the maximum, an optional comment, and an evaluated PDF. A later save replaces them and recalculates pass and rank. A System Admin can read the evaluator name and cannot mark. The student never sees the evaluator. A failed-to-submit row is not marked. §7 | **VERIFIED.** All 13 `LiveEvaluationIT` cases passed, including activation without a password, the second-assignment rejection, the System Admin read-only path, the optional comment, the answer-PDF download, and the written retake staying off the live board. |
| Results and leaderboard | Score, maximum, and pass or fail return as soon as the paper is posted. The live board opens five minutes after the window. Ties share a rank and the next rank skips. A blank paper is included. A student who never tapped is left off. The board shows name, rank, score, maximum, and pass or fail. No phone number. §6.1, §8 | A no-submit zero is on the board. A submitted PDF stays off until a mark is saved. §7.2, §8 | **VERIFIED.** `LiveEvaluationIT.theMcqBoardWaitsFiveMinutesAndSkipsTiedRanks`, `aFailedToSubmitRowIsListedAndCannotBeMarked`, `aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard`. |
| What students must not see | Correct option and explanation before that sitting's deadline. Another student's paper. | Question PDF only for that participant, inline, during the sitting, and only while the student is still marked and the module is still published. Rejected after roster removal, archive, or the deadline. The bytes are not a cached download. The student does not see the evaluator. §§4.3, 7 | **VERIFIED.** `LiveSittingIT.theQuestionPdfDownloadsOnlyDuringTheSitting`, `aRemovedRosterOrArchivedModuleRejectsAnotherSubmission`, `removingTheRosterLeavesTheResultReadable`, `anArchivedAncestorHidesTheExamAndLeavesThePostedResult`, `LiveEvaluationIT.theAnswerPdfDownloadsForTheMarkerAndRejectsEveryoneElse`, `aCommentReachesTheStudentAndABlankCommentStaysAbsent`. Content-Disposition and Cache-Control on the wire were not re-read in this phase. **INFERRED** from the sitting rule and the passing PDF test. |
| Archive | Archiving a category succeeds with children and modules. A student cannot list, open, take, or retake when the module is archived or any ancestor category is archived. Knowing the exam id does not bypass that. A posted result stays readable. An archived category rejects a new child, a new module, a move onto it, and publish (`409 CATEGORY_ARCHIVED`). Delete of an occupied category stays `409 CATEGORY_NOT_EMPTY`. §§3.1, 4.1 | Same hide rule for the written exam and its question PDF. | **VERIFIED.** `LiveCatalogIT.anArchivedCategoryHidesItsBranchAndRejectsNewPlacement`, `LiveSittingIT.anArchivedAncestorHidesTheExamAndLeavesThePostedResult`. |
| Out of scope | No shuffle, negative mark, partial credit, second correct option, second retake, weighted score, question bank, photo pages, a paper that is both MCQ and written, batches, notices, a payment gateway, or proctoring. No exam node on a course tree. §10 | Same. Images are the old model, not this one. | **INFERRED** from the live schema and from the old controllers still being separate types. This phase did not call the old `/api/v1/exam-modules` routes. |

## 5.2 Can the backend run?

| Item | Status | Evidence |
|---|---|---|
| Build | `DONE-VERIFIED` | `api/mvnw.cmd -DskipTests package` finished `BUILD SUCCESS` in 10.2 s at 2026-10-22 21:44 +06. Jar: `api/target/api-0.0.1-SNAPSHOT.jar`, repackaged by `spring-boot:4.1.1`. Java 21.0.5, Maven 3.9.10. `pom.xml` sets `<java.version>21</java.version>` and parent Spring Boot 4.1.1. |
| Start against PostgreSQL | `PARTIAL` | Five Spring contexts started on profile `dev` with the datasource pointed at `jdbc:postgresql://127.0.0.1:5432/coaching_center_test` (PostgreSQL 17.9). Flyway logged that URL. Port 8080 was not listening before the run, and a dev-profile process was not started. `coaching_center_dev` is still at Flyway V23. Starting the app on `api/.env` would apply V24–V28 there, including the `users` role check that adds `INSTRUCTOR`. That write was not approved. |
| Migrations | `PARTIAL` | Files `V1`–`V28` are in `api/src/main/resources/db/migration/`. Live exam files are `V24__live_exam_catalog.sql` through `V28__live_exam_evaluation.sql`. On `coaching_center_test`, Flyway validated 28 migrations and reported schema `public` version 28, up to date, no migration necessary. History shows V24–V28 applied successfully on 2026-10-20. After the tests, the latest row by `installed_rank` is still V28. `coaching_center_dev` is still V23 (`question banks`), also after the tests. A migration onto an empty database was not run. |
| Seed / demo data | `MISSING` | The only startup writer is `MasterAdminBootstrap`. It inserts one Master Admin when none exists, and it returns without changes when one exists. There is no seed for a student, an MCQ exam, or a written exam. Rows that do exist are leftovers. See 5.5. |
| Tests | `BROKEN` | `mvnw test "-Dtest=LiveCatalogIT,LiveExamIT,LiveRosterIT,LiveSittingIT,LiveEvaluationIT"` against the test database. `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` were not in the process, user, or machine environment. This run set them in the shell to `127.0.0.1:5432/coaching_center_test` and the role from `api/.env`. Docker is not on `PATH`, so Testcontainers was not used. Result: **Tests run: 48, Failures: 1, Errors: 0.** `LiveCatalogIT` 7, `LiveEvaluationIT` 13, `LiveExamIT` 6, and `LiveSittingIT` 17 passed. `LiveRosterIT` 4 passed and 1 failed. The classes do not use `@Transactional`, so the run left more rows in the test database. |
| Swagger / OpenAPI | `UNKNOWN` | `springdoc-openapi-starter-webmvc-ui` 3.0.1 is on the classpath. `springdoc.paths-to-match=/api/v1/**`. `SecurityConfig` permits `/v3/api-docs/**` and `/swagger-ui/**` with no profile switch. `application-prod.properties` does not turn that off. No HTTP server was started, so this phase did not fetch the document and does not call it exposed. Re-check on a running profile in Phase 5. |

### The one test failure

`LiveRosterIT.markedStudentSeesPublishedExamsIncludingOnePublishedLater` expected HTTP 201 and got 409.

The helper `createExam` posts the exam, then posts a question. `LiveExamService.create` does not look at the clock, so a draft can be stored with a past `liveAt`. `addQuestion` calls `markDraft`, and `markDraft` throws `CONTENT_LOCKED` when the server instant is not still before `liveAt`. The response body was `{"code":"CONTENT_LOCKED","message":"The question paper and the schedule are locked."}`.

The test hardcodes `liveAt` as `2026-10-20T12:00:00+06:00` and does not install `MutableClock`. Today is 2026-10-22, so that instant is in the past. `LiveExamIT`, `LiveSittingIT`, and `LiveEvaluationIT` pin a mutable clock, which is why their copies of the same date still pass.

This 409 matches scope §4.1: questions and the schedule lock at the live instant. It does **not** show that a marked student fails to see a published exam. That assertion never ran. The product behavior under test is **VERIFIED**. The test fixture is date-locked. Not changed in this phase.

`phase-3-status.md` says slice 2 passed. The slice 2 cases in `LiveSittingIT` and `LiveEvaluationIT` passed today. The status claim holds for those cases. It does not cover the roster visibility case, which did not finish.

## 5.5 Database

Local PostgreSQL 17.9 is running as service `postgresql-x64-17`. `psql` is not on `PATH`. The binary used was `C:\Program Files\PostgreSQL\17\bin\psql.exe`. Port 5432 is listening on `0.0.0.0` and `::`. VPS PostgreSQL was not checked (Phase 6).

Two databases exist: `coaching_center_dev` and `coaching_center_test`. `api/.env` names the dev URL's host, port, and database as `localhost:5432/coaching_center_dev`. Keys present and non-empty in that file: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `WEB_ORIGIN`, `MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME`, `MASTER_ADMIN_PASSWORD`, `DEV_EXPOSE_EMAIL_CODES`, `FILE_STORAGE_DIR`, `PAYMENT_BKASH_NUMBER`, `PAYMENT_NAGAD_NUMBER`. Values are not recorded here.

`ddl-auto=validate`. Flyway is enabled. The dev profile imports an optional `.env`. Prod and staging only set `app.cors.allowed-origins` from `WEB_ORIGIN`.

### Schema versus the brief's names

The brief's names (`exam_attempt`, `student_answer`, `written_submission` plus images) are the replaced model. The live model is the `live_exam_*` tables from V24–V28.

| Brief name | Live table on the test database | Old table, still present |
|---|---|---|
| user / student | `users`, with `student_profiles` from Phase 1 | same identity tables |
| course / enrollment | not used for this exam. Roster is `live_exam_roster` (`module_id`, `student_id`) | `courses`, `exam_module_purchases` |
| exam | `live_exams` | `exam_modules`, `exam_nodes` |
| question / option | `live_exam_questions`, `live_exam_options` | `questions`, `question_options` |
| attempt / answer / result | `live_exam_participants` (`kind` `LIVE` or `RETAKE`, `score`, `passed`, `status`) and `live_exam_answers` | `mcq_attempts`, `mcq_answers`, result tables from V22 |
| written submission | answer-file columns on `live_exam_participants` | `written_submissions`, `written_submission_images` |
| instructor | `INSTRUCTOR` on `users` (V28) and `live_exam_instructors` (one row per module) | `course_instructors` is a different feature |

Live indexes present on `coaching_center_test` (**VERIFIED** from `pg_indexes`, matching the SQL):

- `live_exam_categories_parent_idx` on `(parent_id, position)` where not deleted
- `live_exam_modules_category_idx` on `(category_id, position)` where not deleted
- `live_exams_module_idx` on `(module_id)` where not deleted
- `live_exam_questions_exam_idx` on `(exam_id, position)` where not deleted
- `live_exam_options_question_idx` and unique `live_exam_options_one_correct_idx`
- `live_exam_roster` primary key `(module_id, student_id)` and `live_exam_roster_student_idx`
- `live_exam_participants_one_idx` unique on `(exam_id, student_id)` where not deleted
- `live_exam_answers` primary key `(participant_id, question_id)`

There is no separate index on `live_exam_participants(student_id)`. **UNKNOWN** whether a student-wide lookup would matter. No `EXPLAIN` was run.

`coaching_center_dev` has none of the `live_exam_*` tables and none of those indexes. Status for "schema covers the live exam": `PARTIAL`. It is `DONE-VERIFIED` on the test database and `MISSING` on the dev database.

### What the rows are

Counts taken before the test run. No names, emails, or phones were selected.

`coaching_center_test` (Flyway V28), all of it leftover integration data:

| Item | Count |
|---|---|
| users | 1117, every one on `example.com` or `example.org` |
| by role | 268 `MASTER_ADMIN`, 316 `SYSTEM_ADMIN`, 60 `INSTRUCTOR`, 473 `STUDENT` |
| live categories / modules / exams | 295 / 266 / 234 |
| exams by type and status | MCQ 20 draft, 113 published; written 12 draft, 89 published |
| participants | 246 (live and retake, including `FAILED_TO_SUBMIT`) |
| old `exam_modules` | 0 |

`coaching_center_dev` (Flyway V23), also mostly leftovers, and it has the old exam rows:

| Item | Count |
|---|---|
| users | 124 `MASTER_ADMIN` (all example domains), 172 `SYSTEM_ADMIN` (168 example domains), 242 `STUDENT` (236 example domains). No `INSTRUCTOR` role, which matches V28 not being applied. |
| old exam rows | 375 `exam_modules`, 442 `exam_nodes`, 778 `questions`, 206 `mcq_attempts`, 106 `written_submissions`, 151 `written_submission_images`, 8 `question_banks` |
| live exam tables | absent |

Six student rows and four system-admin rows on the dev database use some other domain. Those addresses were not read. Treat the dev database as not disposable. **INFERRED:** the example-domain mass is the same shared-database leftover that makes `BootstrapIT` expect 1 Master Admin and find many. **UNKNOWN:** whether the ten other-domain rows are accounts Shahriar uses locally. There is no live exam data on dev to preserve, because those tables are not there. The old exam rows are the replaced model.

This machine is not the client VPS. Whether any of this data exists on the VPS is `UNKNOWN` until Phase 6.

## Status counts

Section 5.2 and 5.5 only (10 items):

`DONE-VERIFIED` 2 | `DONE-UNTESTED` 0 | `PARTIAL` 5 | `STUB` 0 | `MISSING` 1 | `BROKEN` 1 | `UNKNOWN` 1

No blocker. The API context starts, Flyway V28 validates, and 47 of the 48 live-exam tests pass. The failure matches a lock the spec requires. The dev database was not migrated and the HTTP server was not started.

## Not done, on purpose

- No empty-database migrate.
- No `spring-boot:run`, no request to port 8080, no Swagger fetch.
- No SSH, no change to `coaching_center_dev`.
- No edit to `LiveRosterIT`.
- No endpoint inventory. That is Phase 2 onward, judged against the extract above.

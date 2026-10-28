# NR-01 final check: untested edge cases and open risks

Written 28 October 2026 on `nr01/integration` at `c0d628d`, after the API part was merged and green (561 tests). **Audience:** the orchestrator and three worker agents (W1, W2, W3). Status: plan, approved by Shahriar ("update the plan to resolve these edge cases and the risks, then run the workers").

This is the **last check before the API is called done**. It adds tests for the rules that no test exercises yet. Where a test exposes a bug, the worker fixes the code. A test is never bent to match a bug.

[../NR-01-exam-segment.md](../NR-01-exam-segment.md) (Decisions and the Record's rulings) and [contracts.md](contracts.md) are the source of the expected behaviour. Each case below quotes the rule it checks. Where this file and those two disagree, they win: stop and ask.

## What is checked, and by whom

| # | Edge case or risk | Rule | Worker |
|---|---|---|---|
| 1 | A standalone exam (no package) through its whole life: listed, sat, scored, ranked, marked, released, counted | R2, contracts §8 | W1 |
| 2 | Publishing staged edits that add or remove questions after a student has started | R16, A4 | W2 |
| 3 | Mid-sitting: a reload shows the new wording and key; an open paper scores against the key in force at submit; the deadline is fixed at start | R17, D15 | W1 |
| 4 | Expired access: retake refused; results and attempts still readable; the question paper is not | A2' | W3 |
| 5 | Package and bundle orders at a discounted price | B5, B6, C2 | W3 |
| 6 | Practice exams: board from first attempts, BOARD and RELATIVE positions, retakes, the instructor's paper read | C9, D5, D6 | W1 |
| 7 | Package instructor assignment with `applyToExams` true and false, and removing the default | R3, D6 | W2 |
| 8 | An exam in an archived package that the student also holds by an exam grant | Archived-package ruling | W3 |
| 9 | Re-scoring on publish for marks, negative marks, pass percent, and multi-answer turned off | E28, A4 | W2 |
| 10 | A staged edit that brings the exam back to its published state removes the revision | A4 | W2 |
| 11 | Archived attempts and written retakes through marking, release, results and the student's history | D6, R14 | W1 |
| 12 | Bulk answer save when answers cannot be changed | R9, D3 | W1 |
| 13 | Staff exam list: each filter on its own, paging, sorting | B3 | W3 |
| 14 | A System Admin grants a written retake | R14 | W2 |
| 15 | Audit rows for the new staff actions | contracts §5 | W2 (exam, instructor, retake), W3 (package, popular) |
| 16 | Two retake requests at the same moment create one attempt | D2 | W1 |
| 17 | **Risk:** the migration has only met test data. Rehearse `V45`–`V51` on a copy of the staging database | F1 | Orchestrator |

## Rules for the workers

Everything in [README.md](README.md) "How agents work" applies. The final-check specifics are below.

- **Branches and worktrees.** All are cut from `nr01/integration` at the plan commit.

  | Worker | Branch | Worktree | `-Agent` |
  |---|---|---|---|
  | W1 | `nr01/fc-w1` | `F:\cc-wt\nr01-fc1` | `W1` |
  | W2 | `nr01/fc-w2` | `F:\cc-wt\nr01-fc2` | `W2` |
  | W3 | `nr01/fc-w3` | `F:\cc-wt\nr01-fc3` | `W3` |

- **Tests go in new classes only**, named below. Do not edit an existing test class, unless a bug fix changes a decided behaviour that an old test asserts the wrong way. Then say so in the progress note.
- **Use the shared fixtures** (`AuthoringFixture`, `PackageFixtures`, and the helpers in the existing `*IT` classes) through copy or composition. Do not change them. A fixture you need goes in your own class.
- **Production fixes.** Each worker may fix production code only in the files listed for it below. A bug in any other file: write the failing test, mark it `@Disabled("bug: <one line>, see report")`, and report it with the exact cause. The orchestrator fixes it on integration.

  | Worker | May fix |
  |---|---|
  | W1 | `LiveExamSittingService`, `LiveExamStudentStatus`, `LiveExamStudentStanding`, `LiveExamLeaderboardService`, `LiveExamEvaluationService`, `LiveExamResultsService`, `LiveExamParticipationService`, `LiveExamSubmissionFeedService`, `StudentLiveExamAccessService` (results part), `LiveExamDeadlineJob`, `LiveExamPublicService`, `LiveExamCatalogVisibility` |
  | W2 | `LiveExamService`, `LiveExamRevisionService`, `ExamChangeListBuilder`, `ExamState`, `LiveExamPreviewService`, `LiveExamInstructorService`, `LiveExamInstructorModules`, `RetakeGrantService` and their controllers |
  | W3 | `LiveExamAccess`, every `LiveExamOrder*` file, `LiveExamGrantAdminService`, `StudentOwnAccessService`, `StaffExamListService`, `PackageMembershipService`, `PackageRevisionService`, `PopularService` and their controllers |

- **Test runs.** Once per case group, in one invocation, through `tools/nr01-mvn.ps1 -Agent W<n> -Tests "…"`. Rerun only failing classes. No `-Full`. Commit each green group at once (a usage limit can cut you off). A WIP commit is fine if you must stop red.
- **A case that turns out to be already covered** by an existing test: cite the test (class and method) in the progress note and skip it. Do not duplicate it.
- **Report.** List each case number as one of: covered by a new test, already covered (cite it), or bug found (fixed, or `@Disabled` and explained). Give the test counts.

## W1: sitting, results and marking

New classes: `StandaloneExamLifecycleIT`, `MidSittingIT`, `PracticeExamIT`, `ArchivedAndRetakeMarkingIT`, `BulkAnswerLockIT`, `RetakeRaceIT`.

### Case 1. `StandaloneExamLifecycleIT`

A standalone MCQ exam and a standalone written exam, each created with `POST /api/v1/live-exams` in an open category and in no package. Access is through an `EXAM` grant (`POST /api/v1/live-exam-grants`, no `moduleId`) for student S1. Student S2 has no grant.

1. **Listing.**
   - `GET /public/live-exam-categories/{cat}/exams` lists both exams, each with `packages: []`.
   - `GET /public/live-exams/{id}` returns 200.
   - `GET /public/live-exams?upcoming=true` includes the dated one.
   - S1's `GET /me/exam-routine` includes it, and S1's `/me/live-exam-access` lists it with `moduleId` null.
2. **MCQ sitting** (clock inside the window). S1 takes, answers and submits. The result has the score and `position` (`CURRENT`). S2's `take` is refused: 404, the "no access" rule.
3. **Leaderboard and staff views** after the window ends:
   - `GET /live-exams/{id}/leaderboard` lists S1 (at or above the pass mark).
   - `GET /live-exams/{id}/results` (staff) has S1's row.
   - `GET /live-exam-participation` has a row for the exam.
   - `GET /students/{S1}/live-exam-results` has the row. A null package must not break any of these. The module title may be null.
4. **Written.** The Master Admin assigns instructor I (`POST /live-exams/{id}/instructor`). S1 takes and uploads a PDF. After the deadline:
   - I sees the paper in `GET /live-exam-submissions` and opens `GET /live-exam-participants/{p}/question-pdf`;
   - I saves marks (evaluation endpoint) and marks it ready;
   - the Master Admin releases (`POST /live-exams/{id}/publish-results`);
   - S1's result shows the marks, and the leaderboard lists S1.
   - A second instructor, not assigned, gets 404 on the paper.

**Expected bug spots** (contracts §8 "display only" reads must tolerate a null package): `LiveExamEvaluationService` near line 476, `LiveExamResultsService` near line 128, `LiveExamSubmissionFeedService` near line 133, the routine view, and `LiveExamPublicService` near lines 342 and 359.

### Case 3. `MidSittingIT`

Published MCQ exam with three questions, window 10:00–12:00, duration 40.

1. S1 starts at 10:10. Their `deadlineAt` is 10:50.
2. The Master Admin stages, then publishes, a change at 10:20: question 1's stem is reworded, its correct option moves from A to B, and `durationMinutes` becomes 60.
3. S1 reloads (`GET /live-exams/{id}`, the student view):
   - the new stem is shown;
   - the key is **not** shown (the sitting is open);
   - `sitting.deadlineAt` is still 10:50.
4. S1 answers B on question 1 and submits. Question 1 earns its marks, because it was scored against the key in force at submit.
5. S2, who submitted before the change with A on question 1: the publish re-scored them, so question 1 now earns 0.
6. S3 starts at 10:30, after the change: deadline 11:30 (the new duration).

### Case 6. `PracticeExamIT`

A published `PRACTICE` MCQ exam (no dates), in a package that S1, S2 and S3 hold.

1. **Board.**
   - S1 scores 8 (first attempt) and retakes to score 10.
   - S2 scores 6.
   - The board shows S1 with 8 (first attempt only) and S2 with 6.
   - S1's retake result has a `RELATIVE` position of rank 1 of 2.
2. **Positions.** S2's first-attempt result is `BOARD`, rank 2 of 2. S3, below the pass mark, sees `belowPass: true` and no rank.
3. **Board visibility.** Students see the full board at once; there is no window to wait for (`ENDED or PRACTICE`).
4. **Instructor paper read on a written practice exam.** The assigned instructor gets 404 on `question-pdf` while a participant's sitting is open, and 200 after that participant's deadline.

### Case 11. `ArchivedAndRetakeMarkingIT`

A written live exam. S1 sits live. S2 sits after the window (`ARCHIVED`). S1 is granted a written retake (by the Master Admin) and sits it (`RETAKE`).

1. All three papers appear in the instructor's submission feed with the right `attemptKind` and `attemptNo`.
2. The instructor marks all three. `publish-results` releases all three.
3. **Leaderboard:** only S1's live paper is on it.
4. **Positions:** S2's result has a `RELATIVE` position. S1's retake result has a `RELATIVE` position, and S1's first result has a `BOARD` position.
5. **History:** `GET /students/{S1}/live-exam-results` has two rows (attempts 1 and 2), and `/live-exams/{id}/results` counts `live` 1, `archived` 1, `retake` 1.

### Case 12. `BulkAnswerLockIT`

An exam with `answersChangeable: false`, one single-answer question and one multi-answer question.

1. `PUT /answers` (bulk) with both questions answered. Then a second bulk call that changes either answer gets `ANSWER_LOCKED`.
2. A bulk call that repeats the same answers is accepted.
3. A bulk call that adds a third, unanswered question keeps the first two unchanged and saves the third.

### Case 16. `RetakeRaceIT`

S1 has a finished MCQ attempt. Two threads call `POST /retake` at the same instant (a `CountDownLatch`).

- **Expected:** exactly one new attempt row. The other call gets `ATTEMPT_IN_PROGRESS` (409), or returns the same open attempt. Never a 500.
- Run the test five times in one invocation (`@RepeatedTest(5)`) to catch flakiness.
- If it is flaky for timing reasons, keep it, report it, and do not delete it.

## W2: authoring, staged edits, instructors

New classes: `StagedPublishGuardIT`, `RescoreVariantsIT`, `RevisionNoOpIT`, `PackageInstructorIT`, `RetakeGrantBySystemAdminIT`, `AuthoringAuditIT`.

### Case 2. `StagedPublishGuardIT`

1. A published MCQ exam, nobody started. Staff stage an added question (allowed while nobody has started).
2. S1 then takes the exam.
3. The Master Admin reads `pending-changes` (it lists the added question) and publishes it. Expected: `409 QUESTION_STRUCTURE_LOCKED`. The exam's published questions are unchanged, and the revision is still there (`pendingChanges: true`).
4. After `DELETE …/pending-changes`, a wording-only edit stages and publishes fine.

Do the same with a staged option removal.

### Case 9. `RescoreVariantsIT`

One published MCQ exam with three submitted papers. Assert each change separately and in one invocation:
- **marks of a question change:** scores change, and `passed` follows;
- **`negativeMarks` 0 → 0.5:** wrong selections lose 0.5, and scores can go negative;
- **`passPercent` raised:** scores are unchanged, but `passed` flips for the paper that now falls short, and the leaderboard drops it;
- **`multipleAnswer` turned off** (two correct options becomes exactly one): papers that chose only the remaining correct option now score full marks.

For each change, the change list `impact.papersToRescore` equals the number of submitted papers, and `live_exam_change_log.rescored` equals the number whose score actually changed.

### Case 10. `RevisionNoOpIT`

A published exam. Change the title (a revision appears), then change it back to the published value. Expected: no revision left, `pendingChanges: false`, `GET …/pending-changes` gives 404, and the badge does not list the exam.

### Case 7. `PackageInstructorIT`

A package with exams E1 and E2. E1 has instructor I1 set per exam.
1. `POST /live-exam-modules/{p}/instructor {instructorId: I2}` with no `applyToExams` (default true). Both E1 and E2 now have I2, and a new exam created in the package gets I2.
2. Set E1 back to I1. Assign I3 with `applyToExams=false`: E1 stays I1, E2 stays I2, and a new exam gets I3.
3. `DELETE …/instructor`: the package default is cleared, the exams keep theirs, and a new exam has no instructor.
4. The instructor directory (`GET /api/v1/instructors`) lists each package under the right people.

### Case 14. `RetakeGrantBySystemAdminIT`

A System Admin grants a written retake (201), and the student can retake. An instructor gets 403 (already covered, so cite it, don't repeat it). A student gets 403.

### Case 15, W2 part. `AuthoringAuditIT`

Assert one `audit_log` row (action, actor, entity) for:
- `LIVE_EXAM_REVISION_PUBLISH`, `LIVE_EXAM_REVISION_DISCARD`;
- `LIVE_EXAM_INSTRUCTOR_ASSIGN`, `LIVE_EXAM_INSTRUCTOR_UNASSIGN`;
- `LIVE_EXAM_RETAKE_GRANT`, `LIVE_EXAM_RETAKE_REVOKE`.

## W3: commerce, access, catalog

New classes: `ExpiredAccessIT`, `PackageDiscountOrderIT`, `ArchivedPackageAccessIT`, `StaffExamListFiltersIT`, `CatalogAuditIT`.

### Case 4. `ExpiredAccessIT`

An MCQ exam and a written exam, each with `accessDays: 30`. S1 holds them by an exam grant made before the window. S1 sits each once. The clock moves past window end + 30 days.

1. `POST /retake` on the MCQ gives `ACCESS_EXPIRED`. The written exam with a grant also gives `ACCESS_EXPIRED`.
2. `GET /live-exams/{id}/result` and `/attempts` still return 200 with S1's data. The written marked PDF stays readable after release.
3. `GET /live-exams/{id}/question-pdf` (student) and the MCQ paper read are refused: 404, the "not open" rule.
4. The access lists show `expired: true`.

### Case 5. `PackageDiscountOrderIT`

1. A package priced 1000 with a 25% discount and no end date: the `MODULE` order amount is 750.00, with `listPrice` 1000 and `discountPercent` 25. After the payment notice, the grant covers every member exam.
2. The same package with a discount that ended yesterday: the amount is 1000.
3. A **bundle** of two exams, one at 200 with 50% off and one at 300 with no discount: the amount is 400.00. Read `LiveExamOrderBundle` for the bundle rule before writing the test.
4. A package discount changed while an order is `INITIATED`: the open order keeps its amount.

### Case 8. `ArchivedPackageAccessIT`

Exam X is in package P. S1 holds P (a package grant) **and** an exam grant on X. S2 holds only P. P is archived.

- S1 can still take X (through the exam grant).
- S2 cannot.
- Both can still read their earlier results on X.
- The mail and notice recipients for X (`findActiveStudentsWithAccessToExam`) include S1 and not S2.

### Case 13. `StaffExamListFiltersIT`

At least six exams across two packages, one standalone, mixed type, mode, status, phase, popular and pending. Check each filter on its own:
- `packageId`, `categoryId`, `standalone=true`, `type`, `mode`, `status`, `phase`, `q` (English and Bangla title), `pending=true`, `popular=true`;
- `page` and `size` (totals, and the second page);
- `sort` by `liveAt`, `title` and `updatedAt`.

### Case 15, W3 part. `CatalogAuditIT`

Assert one `audit_log` row for `LIVE_EXAM_PACKAGE_EXAM_ADD`, `LIVE_EXAM_PACKAGE_EXAM_REMOVE`, `LIVE_EXAM_MODULE_REVISION_PUBLISH`, `LIVE_EXAM_MODULE_REVISION_DISCARD` and `LIVE_EXAM_POPULAR_SET`.

## Orchestrator: migration rehearsal (case 17)

Goal: prove `V45`–`V51` migrate the **real** staging data, not just test data. Everything is read-only on the server. The dump stays on this PC and is deleted afterwards.

1. **Dump.** Over SSH (`exam-kvm`), take a schema-and-data dump of the staging database: `sudo -u postgres pg_dump -Fc coaching`, read-only, streamed to the PC. Do not print it, and do not keep it in Git.
2. **Check the version.** The staging database must be at `V44`. If staging already has later versions, stop and report.
3. **Restore** into a new local database, `cc_rehearsal_nr01`.
4. **Migrate** with the `nr01/integration` code: run Flyway alone against that database. Then check:
   - every exam has `category_id`, one active membership and `ends_at`;
   - former `RETAKE` rows are now `ARCHIVED`;
   - every written participant has a `question_paper_id`;
   - score totals are unchanged;
   - row counts in every touched table are as before.
5. **Start** the API locally against the migrated database (validate only, no seed). Call a few reads: the public catalog, a staff exam list, one student's access list.
6. **Record** counts and timings in `progress/orchestrator-rehearsal.md`. Drop the local database and delete the dump file.

If any step fails, it is fixed in a new migration (`V52`), never by editing `V45`–`V51`.

## After the workers

1. Review each branch: new classes only, fixes within the allowed files, no weakened or deleted assertion.
2. Fix the `@Disabled("bug: …")` reports on integration, and re-enable those tests.
3. Merge W1, W2 and W3. Run the full suite on a fresh database, then web lint, test and build.
4. Update the NR-01 record: each case's outcome, bugs found and fixed, the rehearsal result. Update `ORCHESTRATOR-STATUS.md`.

**Done means:**
- every case is covered by a passing test, or cited as already covered, or reported with Shahriar's OK to defer;
- the rehearsal migrated the staging copy cleanly;
- the full suite and web checks are green on `nr01/integration`.

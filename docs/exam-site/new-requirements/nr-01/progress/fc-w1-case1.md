# FC-W1 case 1: standalone exam through its whole life

**Class:** `api/src/test/java/com/coachingcenter/api/exams/StandaloneExamLifecycleIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "StandaloneExamLifecycleIT"`: 4 tests, 0 failures, 0 errors, 1 skipped (the disabled bug test).

## What is covered

| Test | Checks |
|---|---|
| `theStandaloneExamsAreListedWithNoPackageAndReachedThroughTheRoutineAndAccess` | Public category list shows both exams with `packages: []`; public exam reads 200; `upcoming=true` includes both; S1's `/me/live-exam-access` lists both with no `moduleId`. |
| `theRoutineShowsAStandaloneExamToTheStudentWhoHoldsItByAnExamGrant` | **@Disabled**, see bug below. |
| `aStandaloneMcqExamIsSatScoredPositionedAndShownOnTheBoardsWithNoPackage` | S2 (no grant) `take` 404; S1 `take` LIVE; answer and submit; result score 2, `position.type` CURRENT; after the window the leaderboard lists S1 with score 2; staff `results` has S1's row; `live-exam-participation` and `students/{S1}/live-exam-results` contain the exam. |
| `aStandaloneWrittenExamIsMarkedReleasedAndRankedWithNoPackage` | Instructor assigned to the exam; S1 takes and uploads a PDF; after the deadline the instructor sees the row in `live-exam-submissions` and reads `question-pdf` (200); S2 and an unassigned instructor get 404 on the paper; marks 7 saved, marked ready, published (`publish-results`); S1's result shows 7; leaderboard lists S1; participation and student results contain the exam. |

Counts: 4 tests in the class, 3 running, 1 disabled.

## Bug found (not fixed here, `@Disabled`)

**Routine misses a standalone exam held by an exam grant.**
- Cause: `LiveExamRepository.findVisibleRoutineCandidates` (the first `@Query`, near line 265 of `LiveExamRepository.java`) reaches an exam only through `LiveExamPackageExam`. The `EXAM` grant branch (`ge.scope = 'EXAM' AND ge.examId = e.id`) sits inside the package `EXISTS`, so an exam with no package is never a candidate.
- Effect: `GET /me/exam-routine` returns an empty page for S1, who holds the exam by an `EXAM` grant. Contracts section 8 and case 1 expect the routine to include it.
- Fix needed (orchestrator, `LiveExamRepository.java`, not in W1's list): make the `EXAM` grant branch its own `OR` alternative, outside the package `EXISTS`, and check the exam's home category against the hidden set (`e.categoryId NOT IN :hiddenCategoryIds`).
- Re-enable: remove `@Disabled` from `theRoutineShowsAStandaloneExamToTheStudentWhoHoldsItByAnExamGrant`.

## Notes

- The written paper and answer uploads use a real PDF made with PDFBox (`realPdf()`). The sitting and marking pipeline parses the PDF, so `AuthoringFixture.tinyPdf` is rejected with 400.
- No existing test covered a standalone exam through its whole life. `ExamWindowAuthoringIT.aStandaloneExamIsCreatedInAnOpenCategoryAndBelongsToNoPackage` covers creation and publish only.
- No production file was changed for this case.

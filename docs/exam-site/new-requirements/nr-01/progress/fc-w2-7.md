# NR-01 final check, W2: case 7 (package instructor)

**Rule:** R3, D6. The package default reaches every exam in the package with `applyToExams` (the default). A new exam inherits the default. An exam keeps its own instructor when the default changes with `applyToExams=false`. The instructor directory lists each package under the people who hold it, through an exam or the default.

**New class:** `api/src/test/java/com/coachingcenter/api/exams/PackageInstructorIT.java`

| Test | What it checks |
|---|---|
| `thePackageDefaultReachesEveryExamAndTheDirectoryFollowsEachStep` | Step 1: E1 has I1, then the package default I2 (applyToExams) sets E1 and E2 to I2; a new exam E3 gets I2; the directory lists the package under I2 and not I1. Step 2: E1 back to I1; the default replaced with I3 (`applyToExams=false`); E1, E2, E3 keep theirs; a new exam E4 gets I3. Step 3: `DELETE` the default with `applyToExams=false`; no default row; E1, E2, E4 keep theirs; a new exam E5 has no instructor; the directory still lists the package under I3 through E4. |

**Run:** `nr01-mvn.ps1 -Agent W2 -Tests "PackageInstructorIT"`: 1 run, 0 failures, 0 errors, 0 skipped. One failure on the first run was in the test (the exam-level instructor routes are Master Admin only, contracts §6). It was fixed in the test, not the code.

**Already covered:** `LiveGrantAdminIT` and `LiveEvaluationIT` cover the 409 when an instructor is already assigned, and the single-package assignment. `InstructorDirectoryIT` covers the directory, but not the package and exam split.

**Production code:** no change.

**Two rule questions for the orchestrator (not decided by the decisions file):**

1. **Replacing a package default needs a DELETE first.** `POST /live-exam-modules/{id}/instructor` returns 409 `INSTRUCTOR_ALREADY_ASSIGNED` while a default exists (`LiveExamEvaluationService.assign`, the package row check). The plan's step 2 says "assign I3" with no clear in between. The test clears the default first. Confirm whether POST should replace the default.
2. **A plain `DELETE …/instructor` (applyToExams defaults to true) also clears every exam that holds the default instructor.** That includes an exam that inherited it, so E4 would lose I3 in step 3. The plan's step 3 says "the exams keep theirs". The test sends `applyToExams=false` in step 3, where the plan's wording is unambiguous. The default-true path (`LiveExamEvaluationService.unassign`, line 128 onwards) is not tested here. Confirm which default is wanted. If the plan is right, the fix belongs to W1's file list (`LiveExamEvaluationService`) or the controller, not W2's, so it would be a `@Disabled` test plus a report.

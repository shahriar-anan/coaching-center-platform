# NR-01 final check, W3, case 8: exam in an archived package, held by an exam grant

**Outcome:** new test, green. No production change needed.

**Test:** `api/src/test/java/com/coachingcenter/api/exams/ArchivedPackageAccessIT.java`, 1 test:
`anExamGrantStillOpensAnExamInAnArchivedPackageAndAPackageGrantDoesNot`.

Setup: exam X in package P. S1 holds P (package grant) and an exam grant on X. S2 holds only P. Both sit X in the window. P is then archived (`POST /live-exam-modules/{id}/archive`).

Asserts:
- S1 can still retake X (200, attempt 2). S2 is refused (404).
- Both can still read their first result (`/result?attempt=1`, 200) and their attempts. S1 has 2 attempts, S2 has 1.
- `LiveExamRepository.findActiveStudentsWithAccessToExam(X)` contains S1 and not S2.

**Already covered (for reference):** `LiveSittingIT` covers an archived package on the roster and question-paper reads only. No existing test covers the exam-grant route after the package is archived.

**Test-setup note:** the first run failed on my own attempt-count expectation (S1 has two attempts after the retake), not on the rule. Corrected per student; the rule was not changed.

# FC-W1 case 6: practice exams

**Class:** `api/src/test/java/com/coachingcenter/api/exams/PracticeExamIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "PracticeExamIT"`: 3 tests, 0 failures, 0 errors, 0 skipped.

## What is covered

| Test | Checks |
|---|---|
| `theBoardHoldsFirstAttemptsAndTheRetakeGetsARelativePosition` | Practice MCQ (five two-mark questions, pass mark 5) in a package S1, S2 and S3 hold. S1 scores 8, S2 6, S3 2. S1 retakes to 10. The board has S1 8 and S2 6 (first attempts only). S1's retake is RELATIVE, rank 1 of 2. S2's first attempt is BOARD, rank 2 of 2. S3 has `belowPass: true` and no rank. |
| `theBoardIsOpenToStudentsAtOnceWithNoWindowToWaitFor` | With no window, a student sees the full board straight after the practice exam opens. |
| `theAssignedInstructorReadsAWrittenPracticePaperOnlyAfterTheParticipantsDeadline` | Written practice exam, instructor assigned. The participant's `question-pdf` read is 404 while the sitting is open (04:40Z) and 200 after its deadline (05:16Z, deadline 05:15Z). |

Counts: 3 tests, all running.

## Notes

- Not covered by an existing test. `AttemptsIT.aPracticeExamOpensFromPublishAndItsFirstAttemptIsPractice` covers only the first attempt's kind and deadline. `LeaderboardPositionIT` and `LiveEvaluationIT` cover live windows, not practice.
- The practice mode is set in SQL before publish, as `AttemptsIT` does. The API create body is not used for it.
- No production file was changed for this case.

# NR-01 final check, W2: case 9 (re-scoring on publish)

**Rule:** E28, A4. A change to the marks, negative marks, pass percent or multi-answer flag re-scores the submitted MCQ papers. The change list counts every submitted paper (`impact.papersToRescore`). The change log counts the scores that moved (`rescored`).

**New class:** `api/src/test/java/com/coachingcenter/api/exams/RescoreVariantsIT.java`

| Test | Change | Checked |
|---|---|---|
| `aChangeToTheMarksOfAQuestionChangesTheScoresAndFollowsThroughToPassing` | single question marks 1 to 3 | scores move; a paper that falls short flips to not passed; `papersToRescore` 4; `rescored` 2 |
| `aNegativeMarkValueMakesWrongSelectionsLoseMarksAndScoresCanGoNegative` | `negativeMarks` 0 to 0.5 | wrong selections lose 0.5; a score goes to -1.00; blank stays 0; `papersToRescore` 5; `rescored` 3 |
| `aHigherPassPercentFlipsThePassResultAndDropsThePaperFromTheBoardButKeepsTheScores` | `passPercent` 50 to 70 | scores unchanged; passed flips for the paper below 70 percent; the student board drops it (2 rows to 1); `papersToRescore` 3; `rescored` 0 |
| `turningMultiAnswerOffMakesTheOneRemainingCorrectOptionScoreFullMarks` | multi-answer off; two correct options become one | the paper that chose only the remaining correct option scores full marks; `papersToRescore` 4; `rescored` 3 |

Each test uses its own exam, so the expected scores come from one change only.

**Run:** `nr01-mvn.ps1 -Agent W2 -Tests "RescoreVariantsIT"`: 4 run, 0 failures, 0 errors, 0 skipped.

**Already covered:** `ExamRevisionIT.publishingAKeyChangeRescoresTheSubmittedPapersAndLogsIt` covers a key change only. `McqRescorerIT` covers the re-scorer directly. Neither covers marks, negative marks, pass percent or multi-answer off through the publish path.

**Production code:** no change. `ExamChangeListBuilder.scoringChanged` covers all four changes, and `McqRescorer.rescore` updates both score and `passed`.

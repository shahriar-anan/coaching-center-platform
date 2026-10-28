# FC-W1 case 12: bulk answer save when answers cannot be changed

**Class:** `api/src/test/java/com/coachingcenter/api/exams/BulkAnswerLockIT.java`
**Run:** `tools/nr01-mvn.ps1 -Agent W1 -Tests "BulkAnswerLockIT"`: 1 test, 0 failures, 0 errors, 0 skipped.

## What is covered

A standalone MCQ with `answersChangeable: false`: a single-answer question, a multi-answer question (two correct options) and a third question, all created before the start.

1. The first bulk save answers the first two. Changing the single answer, or the multi-answer selection, is refused with `ANSWER_LOCKED` (409), and the saved options stay as they were.
2. A bulk save that repeats the same answers is accepted.
3. A bulk save that adds the third question keeps the first two unchanged and saves the third.

## Notes

- Not covered by an existing test. `ExamMcqAttemptIT` and `ExamQuestionMultiAnswerIT` cover the answer rules for single calls, not the bulk lock.
- No production file was changed for this case.

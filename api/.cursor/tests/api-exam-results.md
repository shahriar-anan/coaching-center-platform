# api-exam-results tests

Required cases for Phase 3 Stage 8. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a submitted written submission with two snapshotted questions and known max marks, an ended MCQ attempt with a stored score and `immediateResults` false, and a second ended MCQ attempt with `immediateResults` true. One paper has both components. One paper has only MCQ.

## ExamResultsIT

Slice 1.

- A System Admin and a Master Admin can each evaluate an `UNDER_REVIEW` written submission against the snapshotted stems. A student call returns `403`.
- A mark of 0 on one question and a mark equal to `max_marks` on the other are stored, with one feedback string. The written score is the sum of those two marks. Marks below 0, marks above that question's `max_marks`, a missing question, or an extra question id return `MARKS_OUT_OF_RANGE`. Evaluating a `DRAFT` returns `SUBMISSION_LOCKED`.
- A second evaluation before publish replaces each question's marks and the feedback. After publish it returns `RESULTS_PUBLISHED`.
- Publishing a written component that still has an `UNDER_REVIEW` row returns `RESULTS_NOT_READY`. After every under-review row is `EVALUATED`, publish succeeds for every student who has a result. An unsubmitted draft does not block it and does not become a zero.
- Publishing an MCQ component before it is `ENDED` returns `COMPONENT_NOT_ENDED`.
- Before publish, the student MCQ result for `immediateResults` false returns `RESULTS_NOT_PUBLISHED`. After publish, the student receives the stored `score`, `maxScore`, per-question `marksAwarded`, the chosen option, `correct`, and `explanation`. Another student cannot read that result. A student who never started receives `404`.
- The immediate MCQ result includes `score`, `correct`, and `explanation` before publish. Unpublish leaves them visible. It does not include `rank`.
- Before written publish, the student written result returns `RESULTS_NOT_PUBLISHED`. After publish, the student receives each question's marks, the written sum, `feedback`, and the frozen explanation.
- On a two-component paper, before either component is visible, the student paper result returns `RESULTS_NOT_PUBLISHED`. `combinedScore` and `passed` are omitted while the written result is hidden and the MCQ result is visible. Once both are visible, `combinedScore` equals the MCQ score plus the sum of the written per-question marks, and `passed` uses the MCQ attempt's snapshotted pass percent even if the written submission copied a different percent after the paper percent changed.
- On an MCQ-only paper, `passed` is true at the boundary (`score × 100` equal to `passPercent × maxScore`) and false below it. There is no `combinedScore`.
- Admin reads return the stored score, marks, and answer review before publish.

## ExamAnalyticsIT

Slice 2.

- Scores 8, 8, and 5 on one paper produce ranks 1, 1, and 3. A student who never started is absent. The board has no phone number and no email.
- An admin can read that leaderboard before publish. A student receives `RESULTS_NOT_PUBLISHED` until every component on the paper is published. After that, the student leaderboard matches the admin rows, and the caller's paper result includes `rank` and `cohortSize`.
- An immediate MCQ score does not open the student leaderboard before publish.
- Two submitted attempts on one MCQ question, one correct, produce `attempts` 2, `correctCount` 1, and `correctPercent` 50. Each option reports how many attempts selected it. An in-progress attempt is not counted. A student call returns `403`.
- Deleting an option from the live question after those attempts still leaves that option, with its frozen text, in the statistics.
- An evaluated written question with marks 2 and 4 against a maximum of 4 reports `evaluatedCount` 2, `averageMarks` 3, and `averagePercent` 75. An under-review script that is not yet evaluated is not counted.

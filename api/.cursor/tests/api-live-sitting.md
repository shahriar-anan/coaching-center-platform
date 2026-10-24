# api-live-sitting tests

PostgreSQL only. No manual check.

Shared setup: a marked student, a published MCQ exam with three questions, and a published written exam. The MCQ window is 12:00–12:40. Pass percent is 50. Question marks sum to 10.

## LiveSittingIT

- Reading the exam before Take exam returns the name, rules, start, and end, and no stems and no question PDF.
- Take exam at 12:10 stores a participant and returns a deadline of 12:40. A body field `startedAt` does not change it. A second take returns the same participant.
- Saving two answers and omitting the third clears any earlier answer on the third. At the deadline the posted score counts only the saved correct answers. Unanswered is 0.
- Take exam with no saves, then the deadline, posts score 0 and fail. The status is not failed to submit.
- The post response includes score, maximum, and pass or fail, and omits the correct option and the explanation. After the deadline those review fields are present.
- A student who never tapped can start one retake after 12:40. The retake deadline is the tap plus 40 minutes. A second retake is rejected. The live participant's retake start is rejected.
- A written PDF of acceptable size is stored as a compressed PDF. A second upload deletes the first stored file. A file over 80 MB stores nothing. No completed file at the deadline is failed to submit, score 0, and fail.

Slice 1 of these cases passed. Slice 2 has passed. Slice 3 has passed.

## Slice 2

- An early MCQ submit at 12:10 returns the score and hides the review. A save after that submit and before 12:40 replaces the selection. At 12:40 the posted score is the later selection, and the review is present.
- A retake submit before the personal timer ends returns the score and hides the review. A save before that timer ends changes the score posted when the timer ends. The review appears only then.
- The clock passes 12:40 and the student does not call submit. The next read posts the MCQ: saved answers are scored, and a paper with no saves is score 0, fail, and status submitted.
- The clock passes the written deadline and the student does not call submit. No stored PDF becomes failed to submit. A PDF stored before the deadline becomes submitted.
- After Take exam the participant downloads the question PDF bytes. Before the tap the download is rejected. Another student is rejected. During a retake the same download returns the locked file, and after that retake deadline it is rejected.
- Removing the roster row after Take exam leaves the participant row, and the student still receives the result.
- Archiving an ancestor category hides the exam by id, the exam list, take, retake, the question PDF, and the leaderboard. The posted result stays readable. A past exam in a category that was not archived can still be retaken.

## Slice 3

`LiveSittingIT`. PostgreSQL only. Do not change `CourseRoutineIT`.

- A student marked on two published modules receives `GET /api/v1/me/exam-routine` ordered by `liveAt`, then exam id. One row is an MCQ under a child category, so the path is the root and then that child. The other is a written exam in a different module whose window overlaps the first. Each row has the exam id, the type, both exam titles, the module id, both module titles, that category path with both titles, `liveAt`, `endAt`, and the duration. The row has no description, rules, stem, option, correct flag, explanation, or question PDF.
- A draft exam, a final-saved unpublished exam, and a published exam whose module is still a draft are absent.
- A student marked on only one of the two modules receives only that module's exam. A student marked on neither receives an empty page.
- A Master Admin, a System Admin, and an Instructor receive `403`. An anonymous call receives `401`.
- Before the end, an exam that has not started and an exam that has already started are both present. When the server clock reaches the end, that exam is absent. A `now` query does not keep it on the routine. Page size 1 returns the soonest remaining exam, and the ended exam is not that row.
- After the end, the same exam is still on `GET /api/v1/live-exam-modules/{moduleId}/exams`. A student who never tapped can still open it and start the one retake. The routine read before that tap creates no participant.
- A student who already tapped still sees the exam until the shared end. After the end the routine omits it and the result stays readable. A retake whose personal timer is still running stays off the routine.
- Removing the roster row after Take exam drops the exam from the routine, and the result stays readable.
- Archiving the module, its category, or an ancestor drops that branch and leaves the other module's exam on the routine. The posted result stays readable.
- `GET /api/v1/me/exam-routine` is in the OpenAPI inventory.

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

Slice 1 of these cases passed. Slice 2 is open.

## Slice 2

- An early MCQ submit at 12:10 returns the score and hides the review. A save after that submit and before 12:40 replaces the selection. At 12:40 the posted score is the later selection, and the review is present.
- A retake submit before the personal timer ends returns the score and hides the review. A save before that timer ends changes the score posted when the timer ends. The review appears only then.
- The clock passes 12:40 and the student does not call submit. The next read posts the MCQ: saved answers are scored, and a paper with no saves is score 0, fail, and status submitted.
- The clock passes the written deadline and the student does not call submit. No stored PDF becomes failed to submit. A PDF stored before the deadline becomes submitted.
- After Take exam the participant downloads the question PDF bytes. Before the tap the download is rejected. Another student is rejected. During a retake the same download returns the locked file, and after that retake deadline it is rejected.
- Removing the roster row after Take exam leaves the participant row, and the student still receives the result.
- Archiving an ancestor category hides the exam by id, the exam list, take, retake, the question PDF, and the leaderboard. The posted result stays readable. A past exam in a category that was not archived can still be retaken.

# Agent A brief: MCQ attempt pinning (API)

Read first: [README.md](README.md), sections 1 (D9 to D15), 3, 4 and 6.

Goal: every MCQ attempt is scored and shown against the question set it started with. Later edits to the exam never change a running or submitted attempt.

Owned files: see README section 3. You may create `McqAttemptSnapshot.java` and new IT tests.

## Step 0: spike (do this first, before any code)

Report what you find in your report under "Spike results". If a finding breaks the design below, stop and report it. Do not work around it.

1. **Answer foreign keys.** Find the migrations for `live_exam_answers` (likely V27 or V25). Check whether `question_id` or `option_id` has a foreign key to the live question or option tables, and what the `ON DELETE` rule is. Report it. D12 assumes answers point at rows that are never hard-deleted.
2. **Leaderboard.** Read `LiveExamLeaderboardService` and find how rows are ranked and where `maximum` comes from. Report the current ordering and tie-break.
3. **Other maximum readers.** Find every place that shows a maximum to a student or staff member for an MCQ attempt (`LiveExamSittingService.posted`, the student exam view, result views). List them.
4. **Option validation.** Find where `saveAnswers` and `saveAnswer` check that an option id belongs to the question. Report whether they read live rows.

## Tasks

**A1. Migration.** `V65__live_exam_attempt_snapshot.sql`: add `question_snapshot TEXT NULL` to `live_exam_participants`. Use the same style as `V48` and `V50`.

**A2. Entity.** In `LiveExamParticipant.java`, add a `questionSnapshot` String field with getter and setter, following how `ExamState` is stored as a `payload` string in `LiveExamRevision`.

**A3. Snapshot class.** New `McqAttemptSnapshot.java` (package `com.coachingcenter.api.exams`). It:
- builds the snapshot from the live rows of an MCQ exam (non-deleted questions in position order, each with its non-deleted options in position order);
- reads and writes the JSON with the same `ObjectMapper` the other services use;
- gives the answer key as the `McqScoring.QuestionKey` list, and the maximum, from the snapshot.

**A4. Capture at start.** In `LiveExamSittingService.start()`, when the exam is MCQ, store the snapshot on the new participant. Both first attempts and retakes call `start()`, so both are covered.

**A5. Reads use the snapshot.**
- `questionViews(examId, participantId, reveal)`: build the list from the snapshot, not from live rows. Reveal the correct options from the snapshot.
- `key(examId)` used by scoring: use the attempt's snapshot. Scoring is in `scoreMcq`, `posted` and anywhere else that reads the key for a participant.
- `maximum(exam)` for an MCQ result: use the attempt's snapshot maximum. The exam-level maximum can stay live for the exam shell.
- Fallback: if the snapshot is `null`, use the live rows as today. Do not re-score.

**A6. Answer checks use the snapshot.** Any check that an option or question id belongs to the exam must check the attempt's snapshot. Answers to a question that no longer exists in the snapshot are refused as a validation error. This is what keeps a student's answers consistent with their paper.

**A7. Scoring uses the snapshot key.** `McqScoring.total`, `McqScoring.maximum` and `McqScoring.passed` take the snapshot's key and maximum. Check that `McqScoring` already handles a question with no answer, and that `passed` uses the snapshot maximum.

**A8. Leaderboard.** In `LiveExamLeaderboardService`:
- compute `maximum` per attempt (snapshot for MCQ, `writtenMaximum` for written);
- compute `percent` = score ÷ maximum × 100, 2 decimal places, `null` when the maximum is 0;
- rank by `percent` descending, then keep the existing tie-break from Step 0;
- add `percent` to the row DTO (contract in README section 4).

**A9. Do not touch** `McqRescorer` or the publish path. Agent B removes those.

**A10. Tests (write, do not run).** Add IT tests for:
- a question wording change after start: the running attempt still shows the old wording on reload;
- a key change after submit: the score does not change;
- an option count change: an answer given before the change still scores against the attempt's own key;
- an added question: not in an old attempt, present in a new attempt;
- a retake: gets the new snapshot;
- a participant with a `null` snapshot: falls back to live rows;
- the leaderboard ranks by percent across two versions with different maxima.

## Acceptance (what your report must show)

- A reload of a running attempt returns the same question set as at start.
- Scores and maximums for an attempt come from its own snapshot.
- Nothing in your files calls `McqRescorer` or re-scores submitted attempts.
- The leaderboard row has `percent`, and the ranking uses it.
- Every item in the spike is answered.

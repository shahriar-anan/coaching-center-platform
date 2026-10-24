# api-live-sitting

Stage 4. Start only after `api-live-roster` exits. Spec Section 6. Tests: `api/.cursor/tests/api-live-sitting.md`. Rule: `api/.cursor/rules/api-live-sitting.mdc`.

Do not build instructor marking in this stage. MCQ scoring on post is in this stage. The leaderboard open time is the next stage.

## Slice 1 — Participant, MCQ, written PDF, retake

- `Take exam` during the live window creates one participant row for that student and exam. Reading the upcoming card does not.
- A tap at 12:10 on a 12:00–12:40 exam has deadline 12:40. The response includes the server time and that deadline. A client `now` or `startedAt` is ignored.
- Before the tap, the question PDF and the MCQ stems are absent. After the tap, during the sitting, they are present. Correct flags and explanations stay absent.
- MCQ save replaces the full selection. Omitting a question clears it. At the deadline the server posts the saved answers. Unanswered questions score 0. Nothing saved is a blank paper, score 0, and a fail. There is no failed-to-submit status for MCQ.
- The post response includes score, maximum, and pass or fail. It omits the chosen option, the correct option, and the explanation until the sitting's deadline has passed.
- A second tap during the live window returns the same participant. It does not start a second sitting.
- A student who never tapped during the window may start one retake after the end. The deadline is the tap time plus the duration. That sitting is marked as a retake.
- A live participant who calls the retake start is rejected.
- Written upload accepts one PDF up to 80 MB, stores a compressed PDF, and deletes the previous file on replace. Over 80 MB stores nothing. A transfer that has not finished by the deadline does not count. No stored PDF at the deadline sets failed to submit, score 0, and fail.
- The question content used for scoring is the content at the live instant. A later edit is impossible after that instant. The retake uses that same locked content.

## Exit

A late tap still ends at the shared end. A blank MCQ posts as zero with the score and without the review. A missing written PDF is failed to submit. The student who never tapped can take one retake, and a live participant cannot.

Slice 1 of this exit has passed. Slice 2 has passed. Slice 3 has passed.

## Slice 2 — Deadline post, question PDF, result after removal

- An early MCQ submit returns the score of the answers saved at that moment and does not close the sitting. A later save before the deadline replaces the selection. The score at the deadline is that later selection. The chosen option, the correct option, and the explanation stay hidden until the deadline.
- The same rule applies to a retake. An early retake submit hides the review until the personal timer ends. A save before that timer ends changes the score that is posted then.
- When the deadline has passed, the server posts the paper even if the student never calls submit. A read of the exam, the result, or the submission list does this. Nothing saved is score 0 with status submitted. A written exam with no stored PDF becomes failed to submit. A written exam whose PDF was stored before the deadline becomes submitted.
- After Take exam, and until that sitting's deadline, the participant can download the written question PDF bytes. Before the tap the download is rejected. Another student cannot download it. A retake can download the same locked file during the retake timer.
- Removing the roster row after Take exam leaves the participant row, and that student can still read the result.

## Slice 2 exit

An early submit still allows an answer change, and the deadline posts that later selection without a submit call. The question PDF bytes are available only to the participant during the sitting. The result remains readable after the roster row is removed.

## Slice 3 — Exam routine

Spec Section 6.3. No new table, no new permission, and no Flyway change. Do not change the course routine, `V12`, or `CourseRoutineIT`. Do not change the per-module exam list: an ended exam stays on that list.

- `GET /api/v1/me/exam-routine` under `LIVE_EXAM_BROWSE`, which only a student has. Master Admin, System Admin, and Instructor receive `403`. An anonymous call receives `401`.
- The page lists published exams across every published module that student is marked on, where the category branch is still open and the end is still ahead of the server clock. Order is `liveAt` ascending, then exam id. The shared page envelope applies. No rows is an empty page.
- A row has the exam id, the type, both exam titles, the module id, both module titles, the category path from the root with both titles, `liveAt`, `endAt`, and the duration. It omits the description, the rules, the questions, and the question PDF. Times use the same Asia/Dhaka text as the exam card.
- A draft exam, a final-saved unpublished exam, and a published exam in a draft module are absent. An exam that has started and not ended is present. At the end instant it drops off. A client `now` does not move that cutoff.
- Two modules may overlap. Both exams appear. The five-minute gap stays a publish rule inside one module.
- Reading the routine does not create a participant. After the end, the exam remains on the module exam list and can still be opened and retaken. A retake in progress is not a routine row.
- Removing the roster row, archiving the module, or archiving the category or an ancestor drops that exam from the routine. A posted result stays readable.
- Record `GET /api/v1/me/exam-routine` in the OpenAPI inventory so `AuditLogIT` stays green.

## Slice 3 exit

A marked student receives one timetable of live windows that have not ended, across every open module they can sit. An ended exam leaves that timetable and stays available for a retake. The course routine and the module exam list are unchanged.

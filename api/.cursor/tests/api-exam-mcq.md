# api-exam-mcq tests

Required cases for Phase 3 Stage 6. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a student with access to a published module, and a scheduled MCQ component whose window contains the test's server time. A second component has `immediateResults` false. Questions have known marks and one correct option each.

## ExamMcqAttemptIT

Slice 1.

- A student without access receives `EXAM_ACCESS_REQUIRED` and no attempt row is created.
- Starting while the derived lifecycle is `SCHEDULED` or `ENDED` returns `COMPONENT_NOT_ACTIVE`.
- Starting inside the window creates one `IN_PROGRESS` attempt. `started_at` is the server time. A body that includes `startedAt` in the future does not change the stored instant. The response includes `serverNow` and `deadlineAt`. The copied `duration_seconds` stays the same after the component duration is edited.
- The in-progress payload includes the frozen stems and options and omits `correct`, `explanation`, and `score`. Editing the live stem after start leaves the attempt payload unchanged. Another student receives `403`.
- Saving a correct option and a wrong option, leaving one question unanswered, then submitting, stores `score` as the correct question's marks only and `maxScore` as the sum of the frozen marks. The wrong and unanswered questions add 0. Saving the same question again replaces the selection. A later save whose `answers` array omits a previously saved question clears that selection.
- A start on a component with no questions returns `COMPONENT_EMPTY` and creates no row.
- With `immediateResults` true on an MCQ-only paper, the submit response includes `score`, `maxScore`, `passed`, each `marksAwarded`, the chosen option, `correct`, and `explanation`. `passed` follows the pass percent copied at start: a score of 5 against a maximum of 10 and a percent of 50 passes, and a score of 4 fails. Changing the paper's pass percent after start leaves `passed` unchanged. The response has no `rank`. The same submit on a paper that also has a written component omits `passed`. With `immediateResults` false, the student response omits the score and the review, and an admin attempt read still returns them.
- A second start returns `ATTEMPT_ALREADY_USED`. `GET` returns the same in-progress attempt.
- Setting `started_at` in the database so the copied duration has already elapsed, then saving an answer, returns `ATTEMPT_EXPIRED` and leaves the previous answer in place. The attempt is `SUBMITTED`. The same expiry happens when `endsAt` is already past, even if the copied duration has not elapsed. A submit call in that state does not raise the score and does not move `submitted_at` later than the deadline the server used.
- Changing the live correct option after start, including before submit, leaves the stored `score` on the frozen answer. A student who never started does not appear in the admin attempt list.
- A second student can start their own attempt on the same component.
- Archiving the module after a start blocks a new start and still lets the owner save and submit until the deadline.

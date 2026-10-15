# api-exam-mcq

Stage plan for Phase 3 Stage 6. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Section 9 and Stage 6. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-mcq.md](../tests/api-exam-mcq.md). The stage rule is [api-exam-mcq.mdc](../rules/api-exam-mcq.mdc).

Start only after `api-exam-purchase` exits. Do not start `api-exam-written` until this stage exits.

Do not build written uploads, admin evaluation, or a publish action. Immediate visibility is the Stage 3 flag. Hiding and later revealing a non-immediate score is completed in `api-exam-results`; this stage stores the score and returns it to the student only when `immediateResults` is true.

When the slice's checks finish, write `api/.cursor/progress/api-exam-mcq/slice-1-attempt.md`.

## Locked reading

A start is accepted only when the derived lifecycle is `ACTIVE` and the Stage 4 predicate is true. The module's publish status does not decide `ACTIVE` or `ENDED`. The attempt's deadline is the earlier of two server times: `started_at` plus the `duration_seconds` copied onto the attempt at start, and the component's `endsAt`. `endsAt` is read live, so the window still closes an in-progress attempt. The copied duration does not grow if an admin later edits the component. Expiry and scoring use `Instant.now()`. A client-supplied timestamp is not a field on these routes; if the JSON contains `now`, `startedAt`, or `submittedAt`, ignore it for the decision and store the server instant.

The in-progress payload includes `serverNow` and `deadlineAt` so a client can draw a countdown. Those fields do not accept a client value.

Start copies the component's questions onto the attempt: position, stem, explanation, marks, and each option's text, position, and correct flag. It also copies the paper's `passPercent`. Scoring, pass or fail, and the answer review use that copy. Editing the live question or the paper's pass percent afterward does not change this attempt. Order is the copied order. Do not shuffle.

While the attempt is `IN_PROGRESS`, the student payload omits `correct`, `explanation`, `score`, and `passed`. When `immediateResults` is true, the submitted payload includes the score and the answer review. On an MCQ-only paper it also includes `passed`, which is true when `score × 100` is at least `passPercent × maxScore`. On a paper that also has a written component, this payload omits `passed`; the paper result computes it once both scores exist. Rank and the leaderboard are the results stage and stay off this payload.

Unanswered questions score 0. The snapshotted correct option scores that question's full marks. Any other option scores 0. There is no negative mark, no partial mark, and no second correct option.

A component with no questions returns `409`, code `COMPONENT_EMPTY`, and creates no attempt.

If the module is archived or access is lost after the attempt is `IN_PROGRESS`, the owner may still read, save, and submit until the deadline. A new start still requires access. An expired attempt finalizes on the owner's next call and on an admin read.

## Slice 1 — Attempt, timing, and scoring

Flyway `V15__mcq_attempts.sql`.

`mcq_attempts`: component id, student id, `started_at`, `duration_seconds`, `submitted_at` nullable, `score` numeric nullable, `max_score` numeric, `pass_percent` integer, `status` (`IN_PROGRESS`, `SUBMITTED`). One non-deleted row per component and student.

`mcq_attempt_questions`: attempt id, question id, position, stem, explanation nullable, marks. `mcq_attempt_options`: attempt-question id, source option id, text, position, `correct`.

`mcq_answers`: attempt id, attempt-question id, attempt-option id nullable, `marks_awarded`.

Student routes require the Stage 4 access predicate. A false result returns `403`, code `EXAM_ACCESS_REQUIRED`.

- `POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/attempts` starts an attempt. The component must be an MCQ component in `ACTIVE`. Otherwise `409`, code `COMPONENT_NOT_ACTIVE`. A second start returns `409`, code `ATTEMPT_ALREADY_USED`. The owner resumes through `GET`.
- `GET` on that path returns the caller's attempt, including `serverNow` and `deadlineAt`. While `IN_PROGRESS`, the body includes the frozen stems and options in the frozen order and omits `correct`, `explanation`, and `marksAwarded`. Another student receives `403`.
- `PUT .../attempts/answers` saves at most one option per frozen question. The `answers` array is the full selection for this call. Sending a question again replaces that question's option. A question id that is absent from the array has its stored option cleared. An empty array clears every selection. An option that is not on that frozen question returns `400`, code `VALIDATION_FAILED`.
- `POST .../attempts/submit` scores immediately from the frozen correct flags, sets `SUBMITTED`, and stores `score` as the sum of `marks_awarded` and `maxScore` as the sum of the frozen marks. When `immediateResults` is true the student body includes `score`, `maxScore`, each question's `marksAwarded`, the chosen option, `correct` on each frozen option, and `explanation`. It includes `passed` only when the paper has no written component. When `immediateResults` is false the student body omits `score`, `maxScore`, `passed`, `marksAwarded`, `correct`, and `explanation`. Admin `GET /api/v1/exam-components/{componentId}/attempts` under `EXAM_READ` includes the score and the review in both cases. There is no row for a student who never started. The response has no `rank`.
- If `Instant.now()` is at or after the attempt deadline, a save returns `409`, code `ATTEMPT_EXPIRED`, and does not change answers. The attempt is finalized first with the answers already stored. A submit after that instant finalizes if still in progress and does not extend the timer. A long duration does not keep the attempt open after `endsAt`. A short duration finalizes the attempt while the window is still open.
- Tests that need expiry set `started_at` in the database to the past. They do not sleep and they do not send a client clock.
- Changing the live question's stem, options, or correct option after start does not change the frozen payload or the stored `score`, including a change made before submit.
- Changing the component duration after start does not change the attempt's copied `duration_seconds`. Shortening `endsAt` to the past finalizes an in-progress attempt on the next call.

## Stage exit

A student can attempt an MCQ component once, cannot extend the timer with a client clock, and the API stores an automatic score on submission. When immediate results are on, the student sees that score, pass or fail, the correct option, and the explanation.

# Stage 2: Sitting, scoring, results and marking (agent D)

**Covers** decisions C9, C11, C13, C14, D15, D19–D25, E29 (evaluator side), R3, R8, R9, R11–R14, R17 and R18. Read them in [../NR-01-exam-segment.md](../NR-01-exam-segment.md).

Branch `nr01/d-sitting`. Worktree `F:\cc-wt\nr01-d`. Test database `cc_test_nr01_d`. The files you own are in [README.md](README.md#ownership).

## D1: First attempt (take)

**`POST /live-exams/{id}/take`**:
- The student needs `LiveExamAccess.canSit`. If they hold access that has expired, return `ACCESS_EXPIRED`.
- The exam must allow a first attempt (`ExamWindow.canStartFirstAttempt`): a practice exam is open from publish; a live exam needs `liveAt` set and now ≥ `liveAt`. An undated or upcoming exam returns `SITTING_NOT_OPEN`.
- **If attempts already exist:**
  - an open one is returned, as today;
  - otherwise, return `SITTING_NOT_OPEN` and tell the student to use retake. Keep today's message style.
- **The new attempt:**
  - `attemptNo = 1`;
  - kind from `ExamWindow.firstAttemptKind`: `LIVE`, `ARCHIVED` or `PRACTICE`;
  - `deadlineAt = ExamWindow.deadline(…)`;
  - written exams also set `questionPaperId` to the exam's **published** paper.
- **Live or archived is decided at start and never changes (R18):** the kind is stored once and nothing recomputes it.

**Tests:** update `LiveSittingIT`. New `AttemptsIT`:
- a start one second before `endsAt` is `LIVE`, and its deadline runs past the window end;
- a start at `endsAt` is `ARCHIVED`;
- practice;
- upcoming is refused;
- moving the window afterwards does not change the kind;
- expired access is refused.

## D2: Retakes

**`POST /live-exams/{id}/retake`**:
- **MCQ:** the student needs `canSit`, at least one finished attempt (`SUBMITTED` or `FAILED_TO_SUBMIT`), and no open attempt (`ATTEMPT_IN_PROGRESS`).
  - Creates `attemptNo = max + 1`, kind `RETAKE`, deadline = now + duration.
  - Allowed **during the window too** (R11).
  - Unlimited.
- **Written:** the student needs `canSit`, a finished attempt, and an active, unused retake grant. Otherwise return `RETAKE_NOT_ALLOWED`.
  - Creates a `RETAKE`, marks the grant used (`used_participant_id`), and sets `questionPaperId` to the published paper.
  - **At most one written retake ever.**
- A student with **no** attempt calling retake:
  - on an MCQ exam whose window has ended, it behaves exactly as `take` and creates the `ARCHIVED` first attempt. This is [contracts.md §7](contracts.md#7-compatibility-rules-the-current-website-keeps-working) item 9; the current website's later-attempt button depends on it.
  - otherwise, `RETAKE_NOT_ALLOWED`.
- **Legacy fields (contracts §7 items 7–8):** every response that carries `kind` keeps the `LIVE | RETAKE` vocabulary and adds `attemptKind`. `myStatus` keeps its nine values and adds `myStatusDetail`.

**Retake grants:**
- **`POST /live-exams/{id}/retake-grants`** takes `{studentId, reason?}`. Master Admin or System Admin. Written exams only, otherwise `EXAM_TYPE_MISMATCH`. The student must have a finished attempt and **no written retake yet**, else `RETAKE_NOT_ALLOWED`. An active unused grant gives the existing `GRANT_ALREADY_ACTIVE`. Audit `LIVE_EXAM_RETAKE_GRANT`, and call F's `StudentNoticeWriter.retakeAvailable(examId, studentId)`.
- **`DELETE …/retake-grants/{grantId}`** revokes while unused. Audit `LIVE_EXAM_RETAKE_REVOKE`.
- The student result and the staff result rows show `retakeGrant {id, used}`.

**Tests:** new `WrittenRetakeGrantIT`:
- with no grant, retake is refused;
- grant then retake succeeds;
- once the retake is used, a new grant is refused (`RETAKE_NOT_ALLOWED`); a second grant while one is unused gets `GRANT_ALREADY_ACTIVE`;
- revoke an unused grant;
- an instructor cannot grant;
- the audit row is written.

New cases in `AttemptsIT`: MCQ retake three times, during and after the window; refused while one is open.

## D3: Saving answers

**`PUT /live-exams/{id}/answers`** (bulk) and **`PUT /live-exams/{id}/answers/{questionId}`** (one):
- **Payload:** each item is `{questionId, optionIds}`. The old `{questionId, optionId}` is still accepted. The options must belong to the question.
- **Option count:** a question with `multipleAnswer = false` takes at most one option, otherwise `VALIDATION_FAILED`. An empty list clears the answer.
- **When `answersChangeable` is false (R9):**
  - **Single answer:** the first saved non-empty answer is final. A later different value gets `ANSWER_LOCKED`, and so does clearing it. Re-sending the same value is fine (idempotent).
  - **Multi answer:** the student can change the selection until they send `confirm: true` on the single-question route, which makes it final. The bulk route treats any question present in the payload as confirmed.
  - Store the confirmation in `live_exam_answer_confirmations` (F's `V47`). A single-answer question is confirmed by its first non-empty save.
- **Students mid-sitting (R17):** questions are read from the published rows, so a mid-sitting reload shows the new wording and key.

**Tests:** new `MultiAnswerScoringIT`, covering the payload compatibility, multi save, the single-answer cap, the locked single answer, the multi confirm, and clearing a locked answer refused.

## D4: Scoring and the result at submit

- **Posting** (at submit or deadline) scores through `McqScoring.total` with the exam's `negativeMarks`. `passed` comes from `McqScoring.passed`. The score is a decimal.
- **`GET /live-exams/{id}/result?attempt=`** defaults to the latest attempt.
- **MCQ (D21'):** once that attempt is submitted or posted, return the score, maximum, passed, every question with the chosen options, the correct options and the explanation **immediately**, even while the window is still open. **Stop reading `answers_released_at`** (P-d).
- **Written:** as today. The marks, comment and marked PDF appear only after release; a failed-to-submit zero is shown at once.
- **`position`** (shape in [contracts.md §6](contracts.md#d-sitting-results-marking)), computed by `LiveExamLeaderboardService`:
  - **live attempt during the window:** `CURRENT`, the rank among board attempts posted so far;
  - **live attempt after the window, or a practice first attempt:** `BOARD`;
  - **archived attempt or retake:** `RELATIVE`, the rank this score would take among the posted board attempts. Ties take the better rank, and `of` is the number of board rows;
  - **below the pass mark:** `belowPass: true`, `rank: null` (R13);
  - **written:** `position` appears only once that attempt is released, compared against the released and failed-to-submit board rows.
- **`GET /live-exams/{id}/attempts`** lists the student's own attempts.

**Tests:** new `LeaderboardPositionIT`:
- a live submit at 10:30 sees `CURRENT` rank 2 of 3;
- an archived submit sees `RELATIVE` without being on the board;
- tie ranking;
- below pass;
- negative marks change the order;
- a retake never appears on the board and gets `RELATIVE`;
- written position appears only after release;
- MCQ answers are visible at submit during the window.

## D5: Leaderboard

- **`LiveExamLeaderboardService`** replaces `LiveExamEvaluationService.leaderboard`; keep the route.
- **Rows:** board attempts, meaning `LIVE` for a live exam or `PRACTICE` (first attempt) for a practice exam.
  - **MCQ:** submitted or posted.
  - **Written:** released, plus failed-to-submit zeros, as today.
  - **Pass filter (D24):** only scores at or above the pass mark are listed.
  - **Order:** score descending, then earliest `submittedAt`, then name. Ties share the better (competition) rank.
- **Who sees the rows:**
  - **Students:** full rows only when the phase is `ENDED` or `PRACTICE`. During `LIVE` they get `rows: []` plus `me: position` (R12). Drop the old "window end + 5 minutes" rule.
  - **Staff** see the rows at any time, plus `belowPass` counts.

**Tests:** update the leaderboard tests in `LiveEvaluationIT`, plus the cases in `LeaderboardPositionIT`.

## D6: Instructor on the exam and the paper version for evaluators

- **`POST/DELETE /live-exams/{id}/instructor`** (Master Admin). Audit `LIVE_EXAM_INSTRUCTOR_ASSIGN` and `_UNASSIGN`.
- **The package route** `POST /live-exam-modules/{moduleId}/instructor` sets the package default. With `applyToExams=true` it also sets every member exam. `DELETE` clears the default only.
- **Every "assigned instructor" check** (marking, submissions list, question PDF, release status) uses `live_exams.instructor_id`.
- **`LiveExamInstructorModules`** becomes "packages containing at least one exam assigned to me". The instructor's `GET /live-exam-modules` uses it. Test it in `LiveEvaluationIT`.
- **Evaluators and students read the participant's paper.** The question PDF for an evaluator, and for a student in their own sitting, is the participant's `questionPaperId` version, never the exam's current one (E29). The time rule for instructors is unchanged: after the window, published only. For a practice exam: after that participant's deadline.
- **Archived attempts and written retakes** enter marking and release like any paper. `release-status` and `publish-results` count them. `publishAll` releases every ready attempt of the exam, of every kind.

**Tests:** update `LiveEvaluationIT`. New `PaperVersionIT`:
- a student sits v1, the paper is replaced and published (through A's endpoint once merged; until then, insert v2 through repositories and say so);
- the evaluator and the student still get v1;
- a new sitter gets v2.

## D7: Staff counts and results

- **Results report** (`LiveExamResultsService`): the summary splits counts into `live`, `archived`, `retake` and `practice`. Rows carry `kind` and `attemptNo`. There is a filter `kind`.
- **Participation** (`LiveExamParticipationService`) and the **submission feed**: split the counts the same way. Instructor scope follows D6.
- **Student history** (`history`, and `StudentLiveExamAccessService` results): one row per attempt, with `kind` and `attemptNo`.

**Tests:** update `LiveEvaluationIT` and the participation tests. New cases where one exam has live, archived and retake attempts.

## D8: Student status, routine, deadline job

- **`LiveExamStudentStatus`** is rewritten from the phase, access and attempts:
  - **existing statuses keep their names;**
  - **new statuses:** `ARCHIVED_OPEN` (phase `ENDED`, `canSit`, no attempt), `PRACTICE_OPEN`, `EXPIRED`. These go out in **`myStatusDetail`**; `myStatus` maps them to legacy values per contracts §7 item 8;
  - **`retakeAvailable`:** MCQ is true after a finished attempt when `canSit` holds; written is true when an unused grant exists.
- **Routine:** `endAt` is the window end, so no change is needed beyond the field source. Include practice exams? No: the routine is dated exams only.
- **`LiveExamDeadlineJob`** already settles each participant at its own deadline. Confirm that it handles several attempts and archived deadlines long after the window, and add a test for an archived attempt posted by the job.
- **`StudentReminderJob`:** unchanged, but check that it reads `liveAt` only.

**Tests:** update `LiveExamStudentStatusTest`. Add cases for each new status.

## Not yours

- Exam and question write paths, revisions and re-score (A). Your scores must be correct when A's re-score runs, and `McqRescorer` is F's.
- Access rules (C).
- Catalog (B).

## For web-impact

Write to `web-impact/d.md`:
- the radio-circle answer control;
- the multi-answer "select all that apply" and confirm button;
- the attempts list;
- the retake button rules;
- the result page with answers at submit and the position messages;
- the leaderboard empty during the live window with "your position";
- the staff retake grant;
- the per-exam instructor;
- the split counts.

# api-exam-results

Stage plan for Phase 3 Stage 8. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Sections 10 and 11, and Stage 8. Immediate MCQ visibility is Section 9. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-results.md](../tests/api-exam-results.md). The stage rule is [api-exam-results.mdc](../rules/api-exam-results.mdc).

Start only after `api-exam-written` exits. Do not start `api-question-bank` until this stage exits.

Both admin roles evaluate and publish. This is not a delete permission.

When a slice's checks finish, write `api/.cursor/progress/api-exam-results/slice-<number>-<name>.md` before the next slice.

## Permissions

| Permission | Master Admin | System Admin |
|---|---|---|
| `EXAM_EVALUATE` | yes | yes |

## Locked reading

Written marks are entered per question, against the snapshot taken at final submit. Each value is from 0 through that question's `max_marks`. The written score is the sum of those values. One `feedback` string covers the submission. Feedback is a single language and may be blank.

`combinedScore` on the student paper result is present only when both component scores are visible to that student. It is the MCQ score plus the written per-question sum. It is omitted while either score is still hidden, and omitted when the student has no score for one of the two components. A missing attempt is not stored as zero. A one-component paper has no combined field.

Publish and unpublish apply to the whole component. They are not a per-student switch.

A visible MCQ result includes the answer review frozen on the attempt: the chosen option, `correct` on each option, and `explanation` (Section 11.3). A visible written result includes the frozen `explanation`. Both stay off the student payload while that component's score is hidden. An admin read includes the review before publish.

`passed` is on the paper result. It uses the snapshotted pass percent: the MCQ attempt's copy whenever that student has an MCQ attempt, including when a written submission also copied a later paper percent. Use the written submission's copy only when the student has no MCQ attempt. Do not replace the MCQ copy with the written copy. The student passes when `score × 100` is at least `passPercent × maximum`. The score is `combinedScore` when both components exist and both of that student's scores exist. Otherwise it is the single component score. `passed` is shown when that paper score is visible. On a two-component paper it waits until both component results are visible.

An MCQ component with `immediateResults` true stays visible to the student who submitted, including its review and, on an MCQ-only paper, `passed`. Publish and unpublish do not hide it. Rank and the leaderboard still wait until every component on the paper is published.

## Slice 1 — Evaluation and publish

Flyway `V17__exam_results.sql`. Add `marks_awarded` on `written_submission_questions`. Add `feedback`, `evaluated_at`, and `evaluated_by` on `written_submissions`. Add `results_published` boolean default false on `paper_components`.

- `POST /api/v1/exam-components/{componentId}/submissions/{submissionId}/evaluation` under `EXAM_EVALUATE`, with `feedback` and one `marks` value per snapshot question id. The submission must be `UNDER_REVIEW` or already `EVALUATED`. A `DRAFT` returns `409`, code `SUBMISSION_LOCKED`. A missing snapshot question, an unknown question id, or marks below 0 or above that question's `max_marks` returns `400`, code `MARKS_OUT_OF_RANGE`. Success sets `EVALUATED`. The admin reads the snapshotted stems, not the live question text.
- A further evaluation call may correct marks until that written component's `results_published` is true. After publish, the call returns `409`, code `RESULTS_PUBLISHED`.
- `POST /api/v1/exam-components/{componentId}/results/publish` and `POST .../results/unpublish` under `EXAM_EVALUATE`.
- Publishing an MCQ component requires derived lifecycle `ENDED`. Otherwise `409`, code `COMPONENT_NOT_ENDED`. Publishing a written component requires every `UNDER_REVIEW` submission to already be `EVALUATED`; a remaining `UNDER_REVIEW` row returns `409`, code `RESULTS_NOT_READY`. Drafts that were never submitted do not block publish and do not become zeroes.
- Student `GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/result` returns the caller's MCQ `score`, `maxScore`, per-question `marksAwarded`, chosen option, `correct`, and `explanation` when `immediateResults` is true or `results_published` is true. It returns each written question's `marksAwarded`, `maxMarks`, and `explanation`, the written sum, and `feedback` when that component is published. Otherwise the result body is hidden (`403`, code `RESULTS_NOT_PUBLISHED`) for that component. Another student's id is not a parameter. A caller with no attempt or submission receives `404`.
- Student `GET /api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/result` returns each visible component result, `combinedScore` under the locked reading, and `passed` when the paper score is visible. The MCQ review is included only for a visible MCQ result. A caller who has a submitted MCQ attempt or a submitted written submission, but no component score is visible yet, receives `403`, code `RESULTS_NOT_PUBLISHED`. A caller who never sat receives `404`. An unsubmitted written draft is not a sitting.
- Admin detail still returns stored scores, marks, and the review before publish.

## Slice 2 — Rank, leaderboard, and question statistics

No new table. Rank, the leaderboard, and statistics are computed from submitted MCQ attempts and evaluated written submissions.

The paper score used for rank is `combinedScore` when the student has both component scores, and the single score when the paper has one component. Order is that score descending. Equal scores share a rank and the next rank skips. A student with no sitting is absent. Rank does not use the pass percent and does not apply a weight (Section 11.5).

- `GET /api/v1/exam-papers/{paperId}/leaderboard` under `EXAM_READ` is paged and available before publish. Each row has the student's name, `rank`, `score`, `maxScore`, and `passed`. It has no phone number and no email.
- `GET /api/v1/catalog/exam-modules/{moduleId}/papers/{paperId}/leaderboard` uses the same rows for a student with access, and only after every component on that paper is published. Before that, `403`, code `RESULTS_NOT_PUBLISHED`. The caller's paper result then includes `rank` and `cohortSize`. Immediate MCQ visibility does not open this route.
- `GET /api/v1/exam-components/{componentId}/question-stats` under `EXAM_READ`. A student receives `403`. Counts use frozen questions and options on that component only.
- An MCQ row has `questionId`, `attempts`, `correctCount`, and `correctPercent` (`correctCount × 100 / attempts`, half up, and 0 when `attempts` is 0), plus each option's `selectedCount` and `selectedPercent`. Options chosen on a frozen attempt remain in the list after the live question drops them.
- A written row has `questionId`, `evaluatedCount`, `averageMarks`, and `averagePercent` of that question's snapshotted maximum. In-progress attempts and unsubmitted drafts are not counted.

## Stage exit

An admin can evaluate a written submission and publish results. A student sees the individual result, the answer review, and the combined score when both apply, only once those scores are visible. An immediate MCQ score includes its review without that publish. The paper result includes pass or fail. After publish, a student sees rank and the leaderboard. An admin can read the leaderboard before publish and can read question statistics.

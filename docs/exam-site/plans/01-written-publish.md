# Step 1 — Written publish gate

API only. Shahriar confirmed this plan on 2026-10-23. Flyway `V29`. Do not edit `V14`–`V28`. Package `com.coachingcenter.api.exams`.

Decisions in `../decisions.md` ("Written results") and the rules in `../steps/01-written-publish.md` stay in force. This plan adds the slice order, the column, the call, and the tests.

## Slice 1 — A save is a draft

`V29__live_exam_written_publish.sql` adds one column to `live_exam_participants`:

```sql
published_at TIMESTAMPTZ NULL
```

Null means the written mark is a draft, or that no instructor mark exists. The column has no check constraint. `evaluated_at` stays the time of the latest save. `published_at` is the time of the latest publish.

`LiveExamEvaluationService.evaluate` keeps the current save: marks, pass or fail, optional comment, evaluator id, and the compressed evaluated PDF. It always sets `published_at` to null, including when the Master Admin is the one who saves. A later save still replaces the marks, the comment, and the PDF, and deletes the previous evaluated file.

`SubmissionView` gains `published`, true only when `published_at` is set. Staff still receive `score`, `passed`, `evaluatorName`, and `evaluatedPdf` on a draft. The student result does not gain an evaluator id or name.

`LiveExamSittingService.result` for a written paper:

| Participant | `score`, `passed`, `comment`, `evaluatedPdf` |
|---|---|
| `FAILED_TO_SUBMIT` | Score and pass or fail stay. Comment and evaluated PDF stay absent. Publish is not required. |
| Submitted, no save yet | All four stay absent. `status` stays `SUBMITTED`. `maximum` stays. |
| Saved, `published_at` null | All four omitted. `status` and `maximum` stay. |
| Saved, `published_at` set | Score, pass or fail, comment when present, and `evaluatedPdf: true`. |

`GET /api/v1/live-exams/{id}/evaluated-pdf` returns 404 while `published_at` is null, including when the file is already stored. MCQ `result`, MCQ submit, and the five-minute MCQ board are unchanged.

`leaderboard` still waits five minutes, still lists `LIVE` rows only, and still ranks highest score first with shared ranks. A written live row is included when status is `FAILED_TO_SUBMIT`, or when `published_at` and `score` are both set. A saved draft stays off the board.

## Slice 2 — Master Admin publish

New permission `LIVE_EXAM_PUBLISH` in `Permissions`, granted only to `MASTER_ADMIN` in `LiveExamPermissionContributor`.

```
POST /api/v1/live-exam-participants/{id}/publish
```

This is not `POST /api/v1/live-exams/{id}/publish`, which remains the catalog publish and still requires `EXAM_MANAGE`.

The controller requires `LIVE_EXAM_PUBLISH`. The service also requires `Role.MASTER_ADMIN`. Instructor, System Admin, and student receive `403` `ACCESS_DENIED`. An unknown participant receives `404`.

| Target | Result |
|---|---|
| Written, `SUBMITTED`, stored answer PDF, `evaluated_at` and `score` set, `published_at` null | `200`. Sets `published_at` to the server clock. Returns `SubmissionView` with `published: true`. |
| That same row, already published | `200`. Returns the current view. Does not move `published_at`. |
| MCQ participant | `409` `EXAM_TYPE_MISMATCH` |
| `FAILED_TO_SUBMIT`, or a submitted paper with no save | `409` `EVALUATION_NOT_ALLOWED` |

After a successful publish the student result and `evaluated-pdf` follow the published row in the slice 1 table. A later save clears `published_at` and hides the mark, the comment, and the evaluated PDF again until the next publish. A published `RETAKE` is visible to that student and stays off the live board.

No notice is written in this step.

## Tests

`LiveEvaluationIT` on PostgreSQL. Do not use H2. Run `mvn test "-Dtest=LiveEvaluationIT,LiveSittingIT"`.

Move the student-visibility assertions that currently pass on save:

- `aLaterMarkReplacesThePdfAndChangesTheRank` — after each save the student result omits score, pass or fail, comment, and `evaluatedPdf`, and `evaluated-pdf` is 404. The marked name is absent from the board. After the Master Admin publishes both papers, the student sees the mark and the PDF, and the rank assertions run. The second save hides that paper again until it is published again.
- `aCommentReachesTheStudentAndABlankCommentStaysAbsent` — "Noted" is absent until publish, then present. A later blank comment clears publish. After the next publish, `comment` is absent. The body never contains the evaluator name or id.
- `aWrittenRetakeCanBeMarkedAndStaysOffTheLiveBoard` — the student sees 8 only after publish. The board stays the missed-live zero.

Leave `theMcqBoardWaitsFiveMinutesAndSkipsTiedRanks` on the current MCQ assertions.

Add these cases to the same class:

- A Master Admin save stays `published: false` on the staff read, with score and `evaluatorName` still present. The student result omits the mark until that Master Admin publishes.
- Instructor and System Admin receive `403` on publish. System Admin can still read the draft, including `evaluatorName` and score.
- Publish of an unmarked written paper, a `FAILED_TO_SUBMIT` row, and an MCQ participant are rejected as in the table above.
- A second publish returns 200 and leaves `published_at` unchanged.
- `aFailedToSubmitRowIsListedAndCannotBeMarked` still shows the zero on the board with no publish call. The submitted PDF stays off the board until publish.

## Exit

An instructor save and a Master Admin save are drafts. The student sees the written mark, the comment, and the evaluated PDF only after the Master Admin publishes. A later save hides them again. The written live board uses the same gate, and a failed-to-submit zero stays on it. A published retake stays off that board. MCQ scoring and the MCQ board are unchanged.

## Out of this step

Public catalog, prices, SSLCommerz, Google, notifications, and screens.

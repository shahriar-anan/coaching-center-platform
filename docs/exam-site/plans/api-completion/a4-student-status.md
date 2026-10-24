# A4 — Student status per exam

`decisions.md` lists status pills as still open (D1). Default: build them. The status is derived from the window, the server clock, and the student's own participant row. No migration and no new table.

## Where it appears

Add one field, `myStatus`, plus `retakeAvailable` (boolean), to the **student** views only:

- `GET /api/v1/live-exam-modules/{moduleId}/exams` when the caller is a student
- `GET /api/v1/live-exams/{id}` when the caller is a student
- `GET /api/v1/me/exam-routine` rows

Staff views and the public catalog do not get it. The public read stays anonymous and does not know the caller.

## Status

Evaluate in this order. The first match wins.

| `myStatus` | Condition |
|---|---|
| `NOT_ANNOUNCED` | `live_at` is null |
| `UPCOMING` | now < `live_at` |
| `IN_PROGRESS` | A participant exists, `submitted_at` is null, and now < `deadline_at` |
| `SUBMITTED` | Before `deadline_at`: an MCQ participant that submitted early, or a written participant with a stored PDF. The score may already show. Answers and the PDF can still change. |
| `AWAITING_RESULT` | A written participant at or past `deadline_at` with a stored PDF and `published_at` null |
| `RESULT_READY` | An MCQ participant is past `deadline_at` and posted, or will post. Or a written participant has `published_at` set. |
| `FAILED_TO_SUBMIT` | The participant is `FAILED_TO_SUBMIT`, or a written participant is past the deadline with no PDF |
| `OPEN` | No participant and now is inside [`live_at`, `end_at`). Take exam is possible. |
| `MISSED` | No participant and now ≥ `end_at` |

`retakeAvailable` is true only for an MCQ whose status is `MISSED`. That matches `POST /retake`: one later attempt when the student never tapped Take exam. Written is always false. Once a retake participant exists, the row above uses that participant.

The same function must be used by the routine, the list, and the detail. Put it in one package-private class (`LiveExamStudentStatus`) with a unit test. Load the student's participants for the page with one `findByStudentIdAndExamIdIn` query. The read does not write.

## Tests

`LiveExamStudentStatusTest` (unit, no Spring) covers each status at its boundary instants: `live_at - 1s`, `live_at`, `deadline - 1s`, `deadline`, `end_at`. It also checks `retakeAvailable` for MCQ and written.

`LiveSittingIT` additions:
- The module list shows `OPEN` before Take, `IN_PROGRESS` after, `SUBMITTED` after an early MCQ submit, then `RESULT_READY` after the deadline.
- Written: `IN_PROGRESS` after Take, `SUBMITTED` after the upload, `AWAITING_RESULT` after the deadline and still after a draft save, and `RESULT_READY` after the Master Admin publishes.
- `MISSED` with `retakeAvailable: true` for MCQ and `false` for written.
- A null `liveAt` shows `NOT_ANNOUNCED`.
- The routine and the detail return the same value as the list for the same exam.
- A staff read of the same exam has no `myStatus`.
- One student's status never reflects another student's row.

# A5 — Delete a live exam and an MCQ question

Today `DELETE` exists for categories, modules, and roster rows only. Both tables already have `deleted_at`. No migration.

## Delete an exam

```
DELETE /api/v1/live-exams/{id}
```

| Rule | Value |
|---|---|
| Who | `EXAM_DELETE`, Master Admin only (D2). System Admin, instructor, and student receive `403`. |
| Refused | `409` `EXAM_HAS_PARTICIPANTS` when any participant row exists, live or retake. `409` `EXAM_HAS_PURCHASES` when a `SUCCEEDED` order or a grant with scope `EXAM` exists for it (D3). |
| Effect | Sets `deleted_at` on the exam. Open `INITIATED` orders for that exam move to `CANCELLED` in the same transaction. Pending `exam_outbound_mail` rows for it are skipped at send time by the existing deleted-exam check in `ExamMailTexts`. Stored question files stay on disk. |
| Response | `204`. A second delete is `404`. |
| Audit | `LIVE_EXAM_DELETE` with entity `LIVE_EXAM`. |

After delete, every read treats the exam as missing: staff, student, public, routine, leaderboard, take, and order create. A module grant is untouched. A module checkout still works for the other exams.

Add `EXAM_HAS_PARTICIPANTS` and `EXAM_HAS_PURCHASES` to `ErrorCode`.

## Delete an MCQ question

```
DELETE /api/v1/live-exams/{id}/questions/{questionId}
```

| Rule | Value |
|---|---|
| Who | `EXAM_MANAGE`, the same as add and edit (D4). |
| Lock | The same `markDraft` lock as edit. Once `live_at` has passed, the response is `409` `CONTENT_LOCKED`. |
| Effect | Sets `deleted_at` on the question and its options. Closes the position gap for the remaining questions. Returns the exam to draft with `finallySaved` false. The MCQ maximum follows automatically because it is the sum of the remaining marks. |
| Written exam | `409` `EXAM_TYPE_MISMATCH`, through the existing `requireMcq`. |
| Response | `204`. An unknown question, or one on another exam, is `404`. |
| Audit | `LIVE_EXAM_QUESTION_DELETE`. |

Publishing again still runs the existing `QUESTIONS_REQUIRED` check, so an exam with every question deleted cannot be published.

## Tests: add to `LiveExamIT`

- The Master Admin deletes a draft exam with no participants: `204`, the row has `deleted_at`, and every read is `404`.
- Delete is refused with `EXAM_HAS_PARTICIPANTS` after one Take, and with `EXAM_HAS_PURCHASES` after a succeeded exam order (use `FakeLiveExamPaymentGateway`). An open order becomes `CANCELLED`.
- System Admin receives `403` on exam delete.
- A question delete renumbers positions, lowers the maximum, and returns a published exam to draft.
- A question delete after `live_at` is `CONTENT_LOCKED`. On a written exam it is `EXAM_TYPE_MISMATCH`.
- Deleting the last question and then publishing is `QUESTIONS_REQUIRED`.
- A System Admin can delete a question. A student gets `403`.
- Add both routes to `AuditLogIT`.

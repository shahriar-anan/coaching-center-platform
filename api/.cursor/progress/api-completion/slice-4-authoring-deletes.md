# Slice 4 — A5 authoring deletes

Done 25 October 2026.

- `DELETE /api/v1/live-exams/{id}` (`EXAM_DELETE`, Master Admin). `409 EXAM_HAS_PARTICIPANTS` if any participant row exists, `409 EXAM_HAS_PURCHASES` for a succeeded exam order or an exam grant. Open `INITIATED` orders become `CANCELLED`. Service: `LiveExamRemoval`.
- `DELETE /api/v1/live-exams/{id}/questions/{questionId}` (`EXAM_MANAGE`). Same lock as edit (`CONTENT_LOCKED` after `liveAt`), `EXAM_TYPE_MISMATCH` on written, returns the exam to draft, soft-deletes options, renumbers positions. Publishing an exam with no questions still returns `409 QUESTIONS_REQUIRED` (the plan's text said final-save; it is publish).
- New `ErrorCode`s `EXAM_HAS_PARTICIPANTS`, `EXAM_HAS_PURCHASES`. Audit actions `LIVE_EXAM_DELETE`, `LIVE_EXAM_QUESTION_DELETE`. Both routes added to `AuditLogIT`.
- Result: `LiveExamIT` 11 run, 0 failures (4 new). `AuditLogIT` 2 run, 0 failures.

Next: slice 5, A3 participation counts.

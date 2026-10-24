# Slice 3 — A6 instructor unassign

Done 25 October 2026.

- `DELETE /api/v1/live-exam-modules/{moduleId}/instructor`, `LIVE_EXAM_ASSIGN` (Master Admin). `204`. `404` for a missing module or no assignment. Audit action `LIVE_EXAM_INSTRUCTOR_UNASSIGN`.
- A removed instructor gets `403` (not `404`) on the submission list and evaluate, because the existing checks return `ACCESS_DENIED`. The plan text was corrected.
- Tests added to `LiveEvaluationIT`: takeover keeps the first draft and the second instructor can overwrite and the Master Admin can publish; guard cases. Route added to `AuditLogIT`.
- Result: `LiveEvaluationIT` 20 run, 0 failures. `AuditLogIT` 2 run, 0 failures.

Next: slice 4, A5 deletes.

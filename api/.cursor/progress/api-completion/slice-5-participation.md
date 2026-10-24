# Slice 5 — A3 participation counts

Done 25 October 2026.

- `GET /api/v1/live-exam-participation` (moduleId, type, from, to, q, sort, page, size) and `GET /api/v1/live-exam-participation/summary`. Master Admin and System Admin (`EXAM_READ`), instructor (`LIVE_EXAM_EVALUATE`, assigned modules only; another module is `404`). Student `403`.
- `LiveExamParticipationService`: one grouped SQL query with `COUNT(*) FILTER`, counting a sitting past its deadline as it will post. MCQ rows omit the written-only fields (the API omits nulls).
- Tests added to `LiveEvaluationIT` (4). Routes added to `AuditLogIT`.
- Result: `LiveEvaluationIT` 24 run, 0 failures. `AuditLogIT` 2 run, 0 failures.

Next: slice 6, A4 student status.

# Slice 6 — A4 student status

Done 25 October 2026.

- New `LiveExamStudentStatus` (one pure function) gives `myStatus` and `retakeAvailable` to the student list, the student detail, and the personal routine. Staff views and the public catalog do not carry them.
- The list and routine load the student's participants in one query (`findByStudentIdAndExamIdInAndDeletedAtIsNull`).
- A written paper with a stored PDF before the deadline shows `SUBMITTED` (the plan text was corrected). After the deadline it is `AWAITING_RESULT` until the Master Admin publishes.
- Tests: `LiveExamStudentStatusTest` (7, every boundary instant) and 3 new cases in `LiveSittingIT`.
- Result: `LiveExamStudentStatusTest` 7 run, 0 failures. `LiveSittingIT` 28 run, 0 failures.

Next: slice 7, A1 notices table, result notices, and inbox calls.

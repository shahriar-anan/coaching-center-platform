# Slice 1 — Deadline settle job

Done 25 October 2026.

- Added `LiveExamDeadlineJob` (every minute, single-flight, 500 per run, one transaction per sitting) and `LiveExamParticipantRepository.findDueIds`.
- Flyway `V35__live_exam_deadline_index.sql` adds the partial index. The notices table in A1 is now `V36`.
- Tests added to `LiveSittingIT`: MCQ scored with no read, blank MCQ scores zero, written with no PDF becomes `FAILED_TO_SUBMIT`, a repeat run changes nothing, a deleted sitting is skipped.
- Result: `LiveSittingIT` 25 run, 0 failures, against local PostgreSQL 17 (`coaching_center_test`). This PC has no Docker, so run with `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` set.

**Untested items to add later:** Failure isolation (one bad sitting does not block the others) and the 500-per-run limit.

Next: slice 2, A2 instructor directory.

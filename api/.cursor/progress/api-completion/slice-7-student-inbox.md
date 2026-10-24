# Slice 7 and 8 — A1 student inbox and reminder

Done 25 October 2026.

- Flyway `V36__student_notices.sql`. `StudentNoticeWriter` inserts with `ON CONFLICT DO NOTHING`, only for an active student. `ExamMailQueue` now uses the same insert, which removes the latent aborted-transaction risk.
- `MCQ_SCORE` is written where an MCQ score posts (`scoreMcq`: early submit, deadline, retake). `WRITTEN_RESULT` is written when the Master Admin publish moves `published_at` from null, keyed with that time, so a re-publish after a resave adds a new notice. A save and a failed-to-submit write nothing.
- `StudentReminderJob` (every 15 min, single-flight): published exam and module, branch not archived, `liveAt` within 48 hours, students with roster or module or exam grant. One per student, exam, and start time.
- Inbox: `GET /api/v1/me/notices`, `GET /api/v1/me/notices/unread-count`, `POST /api/v1/me/notices/{id}/read`, `POST /api/v1/me/notices/read-all`. Student only. Marks come from the participant row at read time; a written notice shows none while the result is back in draft.
- Tests (7 added to `LiveEvaluationIT`): MCQ notice once plus deadline job, written publish and resave, failed-to-submit writes none, reminder bands and grants, reminder gates, inbox paging and read state and isolation, staff refused. Four routes added to `AuditLogIT`.
- Result: `LiveEvaluationIT` 31 run, 0 failures.

Next: full suite, then update `todays-goals.md` and `current-api.md`.

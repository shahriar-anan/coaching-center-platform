# Slice 8 — One-hour job

Done. `ExamMailService.scan` runs single-flight every five minutes. The window is after `liveAt` minus 70 minutes and before `liveAt` minus 15 minutes. Both bands are the same `HOUR_BEFORE` row, one per student, exam, and `liveAt`. Access is checked again immediately before send. Lost access marks the row `SKIPPED`. A failed attempt retries the same row up to three times. An exam published inside the window sends exam-time and the one-hour mail. Inside the last 15 minutes only exam-time is sent. The job takes a `Clock`. Tests call `scan()` and do not wait for the scheduler.

`ExamMailIT` passed on PostgreSQL 17, database `coaching_center_test`.

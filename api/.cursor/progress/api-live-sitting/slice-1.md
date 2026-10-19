# Slice 1 — Participant, MCQ, written PDF, retake

Done. Flyway `V27__live_exam_sittings.sql` adds `live_exam_participants` and `live_exam_answers`. Take exam during the window creates the participant. Reading the exam before that returns the name, rules, start, and end, and no stems. A tap at 12:10 keeps the deadline at 12:40, and a client `startedAt` is ignored. A second tap returns the same participant.

Saving answers replaces the whole selection. An early submit returns score, maximum, and pass or fail, and hides the correct option and the explanation until 12:40. A blank MCQ posts as zero with status `SUBMITTED`. A written PDF is stored compressed, a second upload deletes the first file, a file over 80 MB stores nothing, and no stored PDF at the deadline is `FAILED_TO_SUBMIT` with score zero. One retake exists for a student who never tapped. A live participant cannot start it.

Removing the roster row after Take exam leaves the participant row.

`mvn test -Dtest=LiveSittingIT` on PostgreSQL 17.9, database `coaching_center_test`. Flyway applied V27. LiveSittingIT: 5 tests, 0 failures. LiveExamIT and LiveCatalogIT still pass. LiveRosterIT passes after the shared-name search uses a unique name so leftover rows from earlier runs do not change the count.

Next: `api-live-evaluation`.

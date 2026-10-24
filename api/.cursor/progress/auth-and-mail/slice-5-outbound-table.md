# Slice 5 — Outbound table and sender port

Done. Flyway `V34__exam_outbound_mail.sql` adds `exam_outbound_mail` with kind, message key, user id, attempt count, and status. It does not store a code or a rendered body. Kinds are `PAYMENT_RECEIPT`, `EXAM_TIME`, `HOUR_BEFORE`, and `REFUND`. Unique on `(kind, message_key)`. The exam sender is a separate port from account mail. Dev logs “Exam message queued”. Tests use `CapturingExamMailSender` and `MutableClock`. No Brevo call.

`SchemaIT` and `ExamMailIT` passed on PostgreSQL 17, database `coaching_center_test`.

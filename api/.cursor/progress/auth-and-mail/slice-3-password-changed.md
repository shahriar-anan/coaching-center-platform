# Slice 3 — Password-changed note

Done. A successful reset and a successful change-password each send one plain-text note with no code. A failed attempt sends nothing. Dev logs “Password changed message queued” and no code.

`PasswordChangedIT` and `SmtpEmailSenderTest` passed on PostgreSQL 17, database `coaching_center_test`.

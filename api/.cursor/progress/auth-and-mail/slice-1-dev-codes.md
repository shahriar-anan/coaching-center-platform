# Slice 1 — Dev codes

Done. On the `dev` profile, `DEV_EXPOSE_EMAIL_CODES=true` makes `LoggingEmailSender` log the verification, reset, and activation codes. The HTTP body is unchanged. Staging and prod use `SmtpEmailSender`, so the flag cannot print a code. The `.env.example` comment matches that.

`LoggingEmailSenderTest`, `DevExposeEmailCodesIT`, and `DevProfileExposesEmailCodesIT` passed on PostgreSQL 17, database `coaching_center_test`.

# Slice 4 — Activation mail and instructor reissue

Done. Creating or reissuing an activation code also emails it. The admin JSON still returns the code once. A failed send does not roll back create or reissue. Only `PENDING_ACTIVATION` can be issued a code. `POST /api/v1/instructors/{id}/activation-code` requires `ADMIN_MANAGE`. A missing, deleted, or non-instructor id is 404. Student and system-admin reissue of an active or inactive account is 409. No Flyway change. Activation mail does not count against the public auth limiter.

`ActivationMailIT`, `ActivationSendFailureIT`, `ActivationIT`, and `AuditLogIT` passed on PostgreSQL 17, database `coaching_center_test`.

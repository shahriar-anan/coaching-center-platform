# Slice 2 — Who receives a reset

Done. Forgot-password sends a `PASSWORD_RESET` code only for an `ACTIVE` user who is not the Master Admin. Pending, inactive, unknown, and Master Admin get the same public sentence and no mail. Reset of a Master Admin code is refused before the code is consumed. That account still changes its password with the current password.

`PasswordResetAudienceIT` and `PasswordIT` passed on PostgreSQL 17, database `coaching_center_test`.

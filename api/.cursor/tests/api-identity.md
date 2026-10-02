# api-identity tests

Required cases from spec Section 11. The spec's "Section 9 test list" wording means this section. Java tests are written during implementation, not as part of planning.

Run every class against PostgreSQL via Testcontainers. Do not use H2. Replace `ApiApplicationTests`: it boots without a database and will fail once Flyway expects PostgreSQL.

Shared setup:

- Real PostgreSQL — either Testcontainers-managed (CI) or an already-running local instance (local dev), selected via env vars. Never H2.
- A test `EmailSender` that keeps the reset or verification code in memory and does not log it.
- A test profile that tightens login lockout below 5 failures / 15 minutes so the lockout case does not wait out the production window.
- Bootstrap one Master Admin from test environment variables.

## AuthLoginIT

- Login succeeds with a phone identifier.
- Login succeeds with an email identifier.
- Wrong password returns a generic error. The body does not say which part was wrong or whether the account exists.
- A user with `status = INACTIVE` cannot log in and cannot refresh.
- A user with `status = PENDING_ACTIVATION` cannot log in.
- Repeated login failures trigger lockout.

## TokenIT

- Refresh returns a new token pair and invalidates the presented refresh token.
- Presenting an already-rotated refresh token is rejected and revokes every token in that `family_id`.
- Expired or malformed access and refresh tokens are rejected.
- Logout revokes the presented refresh token. That token can no longer refresh.
- `X-Client-Type: web` returns the refresh token only as a cookie.
- `X-Client-Type: mobile` returns the refresh token only in the response body.

## ActivationIT

- A valid, unused, unexpired code sets the password and moves status to `ACTIVE`.
- An expired code is rejected.
- An already-used code is rejected.
- A wrong code is rejected.
- A code that has exceeded its failed-attempt cap is rejected.

## PasswordIT

- `/auth/forgot-password` returns an identical response shape for an existing and a non-existing email.
- `/auth/reset-password` with a valid code sets a new password and revokes all existing refresh tokens for that user.
- `/auth/change-password` requires and checks the caller's current password before accepting a new one.
- No admin endpoint accepts or returns a plaintext password for any user other than the caller's own change-password call. Assert this across the full admin API surface.

## EmailVerificationIT

- `/auth/verify-email` confirms a valid code.
- The resend path issues a new code.

## AuthorizationIT

- `SYSTEM_ADMIN` receives `403` from `DELETE /admins/{id}` and `DELETE /students/{id}`.
- `SYSTEM_ADMIN` receives `403` from `/admins` create and status endpoints, and from `AUDIT_READ` endpoints.
- `MASTER_ADMIN` can soft-delete a student and a System Admin.
- A `STUDENT` cannot read another student's record.
- A `STUDENT` cannot call `/admins` or admin-only `/students` endpoints.
- No reachable endpoint can create, edit the role of, deactivate, or delete a `MASTER_ADMIN`. Test every relevant endpoint.

## DataIntegrityIT

- Soft-deleted users are excluded from list endpoints and cannot authenticate.
- A soft-deleted user's phone and email can be reused by a newly created account.
- Creating a user with a duplicate active phone or email is rejected with a stable error code.

## AuditLogIT

- A delete writes `audit_log` with `actor_user_id`, `action`, and `entity_id`.
- A status change writes the same fields.
- An activation-code issuance writes the same fields.
- A password reset writes the same fields.

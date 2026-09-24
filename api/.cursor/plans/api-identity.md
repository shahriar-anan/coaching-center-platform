# api-identity

Stage 1 implementation plan. The binding spec is [docs/phases/phase-1.md](../../../docs/phases/phase-1.md). This plan adds order only. It does not drop an exit check.

The spec mentions a bootstrap "Section 8.6" that is not in the file. Bootstrap behavior is stated in slice 6. Do not add a forced first-login password change. That flow is not in the phase API surface.

Required test cases are in [api-identity-tests.md](api-identity-tests.md).

Do not start a later slice until the previous slice's checks exist. Do not build courses, batches, enrollments, content, exams, attendance, payments, library, notices, push delivery, file storage, analytics, Google sign-in, SMS OTP, or deployment automation.

## Already on the classpath

Spring Boot 4.1.1, Java 21, Maven, Security, JPA, Flyway, validation, actuator, PostgreSQL driver. Do not replace these.

Add, after checking the artifact matches Spring Boot 4.1: a JWT library used by Spring Security (not an OAuth login provider), Testcontainers PostgreSQL, and a springdoc release compatible with Boot 4.1.

`ApiApplicationTests` boots without a database. Replace it with the Testcontainers suite. Do not leave a context test that starts Flyway against no PostgreSQL.

## Slice 1 — Foundation

Package `com.coachingcenter.api` by feature: `common`, `auth`, `users`. Each feature holds its own controller, service, repository, and DTOs. Do not add a global `controllers`, `services`, or `repositories` package.

- Profiles: `dev`, `staging`, `prod`. Database credentials, JWT secret, and email settings come from environment variables. Do not commit them.
- `ddl-auto=validate`. Schema changes only through Flyway.
- One error body on every error: `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId`. Each `code` names one condition.
- List endpoints use `page` and `size` and one shared page envelope.
- Base entity: UUID `id`, `createdAt`, `updatedAt`, nullable `deletedAt`. Every domain entity extends it.
- JSON logs include the request id. That id is the error body's `requestId`.
- CORS allows only the web origin configured for that profile.
- Never log secrets, passwords, tokens, or one-time codes.

## Slice 2 — Schema

One Flyway migration for `users`, `student_profiles`, `admin_profiles`, `refresh_tokens`, `one_time_codes`, and `audit_log`, matching the columns and constraints in spec Section 6.

- Partial unique indexes on `users.phone` and `users.email` where `deleted_at` is null.
- Store phone numbers as `+8801XXXXXXXXX`.
- Store timestamps in UTC.
- `audit_log` has no update or delete path in the application.

## Slice 3 — Authorization

One role-to-permission map. Check named permissions, not raw roles scattered through services.

Permissions: `STUDENT_READ`, `STUDENT_CREATE`, `STUDENT_UPDATE`, `STUDENT_STATUS`, `STUDENT_ISSUE_ACTIVATION_CODE`, `STUDENT_DELETE`, `ADMIN_MANAGE`, `AUDIT_READ`. Grant them as in spec Section 9.

Every delete endpoint is gated by its own `*_DELETE` permission. Only `MASTER_ADMIN` receives any `*_DELETE` permission. A future module must be able to register a new permission without changing the enforcement engine.

No endpoint creates, edits the role of, deactivates, or deletes a `MASTER_ADMIN`, including a call made by the Master Admin.

## Slice 4 — Auth

All paths under `/api/v1`.

- `POST /auth/register`: student only. Name, phone, email, password. Status `ACTIVE`, password hash set. Send an `EMAIL_VERIFY` code. Login does not wait for `email_verified_at`.
- `POST /auth/activate`: phone, activation code, new password. Sets the hash, status `ACTIVE`, and `used_at`.
- `POST /auth/login`: phone or email, plus password. Requires header `X-Client-Type: web | mobile`. Failure text does not reveal whether the account exists.
- Access token: JWT, 15 minutes, claims are user id, role, and expiry. Resolve permissions from the role on each request.
- Refresh token: 30 days, stored as a hash, rotated on every use within the same `family_id`. A token that already has `replaced_by` revokes the whole family.
- `web`: refresh token only in an httpOnly, Secure, SameSite cookie scoped to the auth path. Never in the JSON body.
- `mobile`: refresh token only in the JSON body. Never in a cookie.
- Access token is always in the JSON body. Later calls send `Authorization: Bearer`.
- `POST /auth/refresh` rotates. `POST /auth/logout` revokes the presented refresh token.
- `POST /auth/change-password` requires the current password.
- `POST /auth/forgot-password` accepts an email and returns the same body whether or not the email exists. A real account gets a `PASSWORD_RESET` code by `EmailSender`. Suggested lifetime: 30 minutes.
- `POST /auth/reset-password` sets the new password and revokes all of that user's refresh tokens.
- `POST /auth/verify-email` confirms a code and has a resend path.
- `GET /me` returns the current user and profile.
- Status other than `ACTIVE` cannot log in or refresh. Deactivation blocks refresh immediately. An already issued access token lasts until its own 15-minute expiry.
- Login lockout: 5 failures per identifier and IP per 15 minutes. Both numbers are configurable so tests can tighten them.
- One-time codes: increment `failed_attempts`. Exceeding the cap invalidates the code.

## Slice 5 — Admin and student management

Admin-created accounts: name, phone, email. Reject the request if a password field is present. Status `PENDING_ACTIVATION`, `password_hash` null. Return the raw activation code once in the response. Do not email it. Re-issue invalidates unused prior codes and still does not accept or return a password.

Endpoints and permissions are spec Sections 8.2 and 8.3.

`EmailSender` sends only password-reset and email-verification messages. The dev implementation must not write the code or link into shared logs. Gate any dev visibility behind a dev-only flag. Staging and prod use an implementation selected by environment configuration. Do not call a provider SDK outside this interface.

## Slice 6 — Bootstrap

If no `MASTER_ADMIN` row exists, create exactly one from environment variables for phone, email, name, and initial password. Store only the password hash.

If one exists, do not create another and do not overwrite the password.

## Slice 7 — OpenAPI and audit

OpenAPI is generated from the code and matches the implemented `/api/v1` endpoints.

Write an `audit_log` row for every delete, status change, activation-code issuance, and password reset. Record `actor_user_id`, `action`, and `entity_id`. Unauthenticated events may have a null actor.

## Slice 8 — CI

During implementation, add `.github/workflows/api.yml`. On every push, build `api` and run the Testcontainers suite. Fail the job if tests fail.

Stage exit: every case in [api-identity-tests.md](api-identity-tests.md) passes against real PostgreSQL, and CI runs that suite.

# Phase 1 Technical Scope: Foundation & Identity

This file is the technical specification for Phase 1 of the Coaching Center Management & Learning Platform. It is written for an engineering agent to read and work from. It defines what to build, the data model, the API contract, and the rules that must hold true. It does not define a day-by-day schedule or non-engineering tasks; the agent should derive its own implementation plan and working rules from this specification.

Treat every requirement below as binding unless a section explicitly marks it optional. Where this document gives a concrete value (token lifetime, header name, table column), use that exact value unless the human operator says otherwise.

---

## 1. Objective

Build the foundation layer and the identity/authentication system for three components that will grow over later phases:

- `api`: Spring Boot backend (Java 21), the single source of truth for all business logic.
- `web`: Next.js admin panel, used only by Master Admin and System Admin.
- `mobile`: Flutter app (Android target for now), used only by Student.

**Two independent, both-required paths create a student account.** Neither is a fallback for the other; both must work, because they serve different real situations:
- **Self-registration:** any prospective student can create their own account directly via `/auth/register`, with no admin involved at all. This is the main path — most students sign up on their own when they decide to buy a course.
- **Admin-created:** for a student admitted offline (in person, cash payment, no app interaction yet), an admin creates the identity record on the student's behalf and hands them an activation code in person.

Having an account, by either path, grants login only. It grants no access to course content — course enrollment is a Phase 2+ concept, entirely out of scope here. Do not build or imply any gate that requires admin approval before a self-registered account can log in.

**Phase 1 is functionally complete when all of the following hold:**
1. A Master Admin can authenticate on `web` and create a System Admin account and a student account (the admin-created path).
2. A student can independently self-register via `/auth/register` with no admin involved (the self-registration path), completely unblocked by anything in item 1.
3. A System-Admin-created or Master-Admin-created student account can be activated on `mobile` using a one-time code, after which the student sets their own password and can log in.
4. No party other than the account owner can ever set, view, or reset that account's password, at any layer including the database (passwords exist only as hashes).
5. Every delete-type action is rejected by the backend for any actor other than the Master Admin, regardless of what the client UI shows.
6. All of Section 9 (testing requirements) passes against a real PostgreSQL instance.

Build only what is needed to satisfy the above. Do not add courses, exams, payments, attendance, or other later-phase domain entities in this phase.

---

## 2. Build Order (backend before clients)

Build and fully test the `api` component before writing UI code in `web` or `mobile`. This is a hard sequencing rule, not a preference:

1. `api` foundation (Section 4) and identity/auth (Sections 5–9), verified against Section 9's test list.
2. A single `mobile` screen (login) wired to the running `api`, to validate the contract end to end from a real client before building further UI. This is a spike, not a full screen set.
3. `web`: full Phase 1 screens (Section 10.1).
4. `mobile`: full Phase 1 screens (Section 10.2).

Do not begin step 3 or 4 until step 1's tests pass. If step 2 reveals a contract problem (wrong field, wrong status code, awkward flow), fix `api` and its tests before proceeding, not the client alone.

---

## 3. Explicitly Out of Scope for This Phase

Do not implement: courses, batches, enrollments, the content tree, lectures, materials, exams, questions, results, attendance, payments, library/books, notices, push notification delivery, file upload/storage, analytics, Google sign-in, SMS OTP, or any production deployment automation. If a task seems to require one of these, stop and flag it rather than building a placeholder for it.

---

## 4. Project Foundation Requirements

### 4.1 Repository and structure
- Three components (`api`, `web`, `mobile`), monorepo or separate repos — either is acceptable.
- `api` is organized **by feature**, not by technical layer: e.g. `auth/`, `users/`, `common/`, each containing its own controller, service, repository, DTOs. Do not use a global `controllers/`, `services/`, `repositories/` split.
- `api` is a modular monolith. Do not introduce microservices, message queues, or separate deployable services in this phase.

### 4.2 Backend stack
- Java 21, Spring Boot, Maven or Gradle.
- PostgreSQL as the only datastore.
- Schema changes only through **Flyway migrations**, checked into version control. Never rely on Hibernate `ddl-auto` for schema management beyond `validate`.
- Spring profiles: `dev`, `staging`, `prod`. All environment-specific values (DB credentials, JWT secret, email provider credentials) come from environment variables, never hard-coded, never committed.

### 4.3 API conventions
- All endpoints under `/api/v1`.
- OpenAPI/Swagger generated from the code and kept accurate; this is the contract `web` and `mobile` are built against.
- One standard error response shape for every error, containing: a stable machine-readable `code`, a human-readable `message`, optional `fieldErrors`, a `timestamp`, and a `requestId`. The `code` must be stable across releases because clients will map it to localized (Bangla/English) text — do not reuse the same code for two different error conditions.
- Pagination: consistent query params (e.g. `page`, `size`) and a consistent envelope for paginated responses across all list endpoints, established now and reused in every later phase.

### 4.4 Cross-cutting technical requirements
- Base entity/mapped superclass with: `id` (UUID, generated), `createdAt`, `updatedAt`, `deletedAt` (nullable, soft delete marker). Every domain entity in this and future phases extends it.
- CORS restricted to the known `web` origin(s) per environment; not wide open.
- Structured logging (JSON or similar) including a request ID per request, propagated through to any error response's `requestId`.
- CI: on every push, build `api` and run its test suite. Fail the build if tests fail.
- Never log secrets, passwords, tokens, or one-time codes, in any environment.

---

## 5. Domain Model: Roles

Exactly three roles exist in this phase: `STUDENT`, `MASTER_ADMIN`, `SYSTEM_ADMIN`.

- **`MASTER_ADMIN`**: exactly one account may exist system-wide. It is created only by the bootstrap process (Section 8.6). No API endpoint may create, modify, deactivate, or delete a `MASTER_ADMIN` record. This is a hard invariant enforced in code, not just by omission — write a test that asserts no reachable endpoint can do it.
- **`SYSTEM_ADMIN`**: unlimited accounts. Created only by `MASTER_ADMIN` via API. Has create/edit access to permitted resources but never delete access to anything, in this phase or any future one. This convention must be reusable: implement it generically enough that later phases add new `*_DELETE` permissions to the same enforcement mechanism rather than inventing a new one.
- **`STUDENT`**: created either via self-registration or by an admin (`MASTER_ADMIN` or `SYSTEM_ADMIN`).

---

## 6. Data Model

Implement the following tables via Flyway migration. Column names are suggestions; types and constraints are requirements.

```sql
-- users
id                  UUID PRIMARY KEY
phone               VARCHAR NOT NULL           -- normalized to +8801XXXXXXXXX before persisting
email               VARCHAR NOT NULL
email_verified_at   TIMESTAMPTZ NULL
password_hash       VARCHAR NULL               -- NULL until account is activated
role                VARCHAR NOT NULL            -- STUDENT | MASTER_ADMIN | SYSTEM_ADMIN
status              VARCHAR NOT NULL            -- PENDING_ACTIVATION | ACTIVE | INACTIVE
last_login_at       TIMESTAMPTZ NULL
created_by          UUID NULL                  -- FK to users.id, null for self-registration
created_at          TIMESTAMPTZ NOT NULL
updated_at          TIMESTAMPTZ NOT NULL
deleted_at          TIMESTAMPTZ NULL

-- student_profiles
user_id             UUID PRIMARY KEY REFERENCES users(id)
full_name           VARCHAR NOT NULL
student_code        VARCHAR NOT NULL           -- unique, system-generated

-- admin_profiles
user_id             UUID PRIMARY KEY REFERENCES users(id)
full_name           VARCHAR NOT NULL

-- refresh_tokens
id                  UUID PRIMARY KEY
user_id             UUID NOT NULL REFERENCES users(id)
token_hash          VARCHAR NOT NULL           -- never store raw token
family_id           UUID NOT NULL              -- groups a rotation chain for reuse detection
client_type         VARCHAR NOT NULL           -- WEB | MOBILE
expires_at          TIMESTAMPTZ NOT NULL
revoked_at          TIMESTAMPTZ NULL
replaced_by         UUID NULL                  -- FK to refresh_tokens.id
created_at          TIMESTAMPTZ NOT NULL
ip                  VARCHAR NULL
user_agent          VARCHAR NULL

-- one_time_codes
id                  UUID PRIMARY KEY
user_id             UUID NOT NULL REFERENCES users(id)
purpose             VARCHAR NOT NULL           -- ACTIVATION | PASSWORD_RESET | EMAIL_VERIFY
code_hash           VARCHAR NOT NULL           -- never store raw code
expires_at          TIMESTAMPTZ NOT NULL
used_at             TIMESTAMPTZ NULL
failed_attempts     INT NOT NULL DEFAULT 0
created_by          UUID NULL                  -- admin who issued it, null for self-service
created_at          TIMESTAMPTZ NOT NULL

-- audit_log
id                  UUID PRIMARY KEY
actor_user_id       UUID NULL                  -- null for unauthenticated events (e.g. failed login)
action              VARCHAR NOT NULL
entity_type         VARCHAR NOT NULL
entity_id           UUID NULL
metadata            JSONB NULL
ip                  VARCHAR NULL
created_at          TIMESTAMPTZ NOT NULL       -- append-only, no update/delete path
```

**Mandatory constraints:**
- Unique constraint on `users.phone` and `users.email` must be **partial indexes** scoped to `WHERE deleted_at IS NULL`, so a soft-deleted user's phone/email can be reused by a new account.
- `audit_log` has no update or delete code path anywhere in the application.
- All timestamps are stored in UTC.

---

## 7. Authentication and Password Design

**Core invariant: no admin, including Master Admin, can ever set, view, or reset another user's password.** There is no endpoint, script, or admin UI action that accepts or returns a plaintext password for any account other than the acting user changing their own. Passwords exist only as hashes (BCrypt). Write an explicit test proving this invariant holds across every admin endpoint (Section 9).

### 7.1 Onboarding flows

**Self-registration (student only):**
1. Student submits name, phone, email, password.
2. Account created with `status = PENDING_ACTIVATION` is not used here — self-registered users go straight to a password, but email must be verified: send an `EMAIL_VERIFY` one-time code. Account is usable for login once created; email verification is a separate, non-blocking step tracked via `email_verified_at`. (If the product owner later wants login blocked until verified, that is a one-line policy change — implement verification as a distinct gate, not entangled with login logic.)

**Admin-created account (System Admin or Student):**
1. Admin submits name, phone, email — no password field exists in this request; reject the request if one is present.
2. Account created with `status = PENDING_ACTIVATION`, `password_hash = NULL`.
3. System generates a one-time `ACTIVATION` code, stores its hash, returns the raw code once in the API response to the admin (never emailed automatically in this phase — the admin hands it over in person or via their own channel).
4. Student/System Admin calls `/auth/activate` with phone + code + a new password of their own choosing. On success: `password_hash` set, `status = ACTIVE`, code marked `used_at`.
5. Admin can re-issue a new activation code (invalidating unused prior ones) if the original is lost — this action never touches or requires a password.

### 7.2 Password recovery
- `/auth/forgot-password`: accepts an email. Always returns the same response whether or not the email exists (no user enumeration). If it exists, issues a `PASSWORD_RESET` one-time code/link, sent via the `EmailSender` abstraction.
- `/auth/reset-password`: accepts the code + new password. On success, revokes **all** of that user's refresh tokens (force re-login everywhere).
- Reset codes are single-use and expire quickly (suggested 30 minutes); attempts are capped (Section 7.4).

### 7.3 Login and tokens
- Login accepts phone **or** email, plus password.
- On success, issue an access token (JWT, 15-minute lifetime, minimal claims: user id, role, expiry — do not embed permissions in the token; resolve permissions server-side on every request from the role) and a refresh token.
- **Refresh token delivery differs by client, determined by a required request header `X-Client-Type: web | mobile`:**
  - `web`: refresh token set as an httpOnly, Secure, SameSite cookie scoped to the auth path. Never present it in the JSON body for web.
  - `mobile`: refresh token present in the JSON response body. Never set as a cookie for mobile.
  - Both: access token returned in the JSON body; subsequent requests carry it as `Authorization: Bearer <token>`.
- Refresh tokens: 30-day lifetime, stored only as a hash, **rotated on every use** — issuing a refresh invalidates the presented token and issues a new one in the same `family_id`. If a token is presented that was already rotated (i.e. already has a `replaced_by`), treat this as token theft: revoke the entire `family_id` immediately.
- Logout revokes the presented refresh token.
- A user with `status != ACTIVE` cannot log in and cannot refresh, even with a currently-valid access token's refresh call.
- Deactivating a user blocks refresh immediately; an already-issued access token remains valid only until its own 15-minute expiry (accepted behavior for this phase, not a bug).

### 7.4 Rate limiting and abuse prevention
- Login: lock out after repeated failures per identifier+IP (suggested: 5 failures / 15 minutes).
- One-time codes: capped failed-attempt count per code (track via `failed_attempts`); exceeding the cap invalidates the code.
- Login failure responses are generic and do not reveal whether the phone/email exists.

### 7.5 Email sending
- Implement an `EmailSender` interface. Provide a dev/test implementation that logs to console (never logs the actual code/link value in a way that could leak into shared logs — treat as sensitive even in dev logging, or gate it behind a dev-only flag). Provide a real implementation wired via environment configuration for staging/prod. Do not hard-code a specific provider's SDK calls outside this abstraction.

---

## 8. API Surface

All paths under `/api/v1`. "Auth: none" means publicly reachable; anything else requires a valid access token, and further requires the stated permission.

### 8.1 Authentication endpoints
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/register` | none | Student self-registration |
| POST | `/auth/activate` | none | phone + activation code + new password |
| POST | `/auth/login` | none | phone-or-email + password; honors `X-Client-Type` |
| POST | `/auth/refresh` | refresh token (cookie or body per client type) | Rotates token |
| POST | `/auth/logout` | authenticated | Revokes current refresh token |
| POST | `/auth/change-password` | authenticated | Requires current password |
| POST | `/auth/forgot-password` | none | Always same response shape |
| POST | `/auth/reset-password` | none | Reset code + new password; revokes all refresh tokens for the user |
| POST | `/auth/verify-email` | none or authenticated | Confirm code; include a resend path |
| GET | `/me` | authenticated | Current user + profile |

### 8.2 Admin management endpoints
| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/admins` | `ADMIN_MANAGE` | Creates a System Admin; request body has no password field; response includes the one-time activation code |
| GET | `/admins` | `ADMIN_MANAGE` | Paginated list |
| PATCH | `/admins/{id}/status` | `ADMIN_MANAGE` | Activate/deactivate |
| DELETE | `/admins/{id}` | `ADMIN_MANAGE` | Soft delete; must reject attempts to target a `MASTER_ADMIN` even if the ID somehow matches |
| POST | `/admins/{id}/activation-code` | `ADMIN_MANAGE` | Re-issues activation code, invalidates prior unused ones |

### 8.3 Student management endpoints
| Method | Path | Permission | Notes |
|---|---|---|---|
| POST | `/students` | `STUDENT_CREATE` | No password field in request; response includes one-time activation code |
| GET | `/students` | `STUDENT_READ` | Paginated, searchable (name/phone/email/student_code) |
| GET | `/students/{id}` | `STUDENT_READ` (own record for STUDENT role) | |
| PATCH | `/students/{id}` | `STUDENT_UPDATE` (own permitted fields for STUDENT role) | |
| PATCH | `/students/{id}/status` | `STUDENT_STATUS` | Activate/deactivate |
| POST | `/students/{id}/activation-code` | `STUDENT_ISSUE_ACTIVATION_CODE` | Re-issue only; never accepts or returns a password |
| DELETE | `/students/{id}` | `STUDENT_DELETE` | Master Admin only (see Section 9) |

---

## 9. Authorization Model

Implement authorization checks against **named permissions**, never raw role checks scattered through business logic. Maintain one central role-to-permission mapping so that changing who can do what is a one-place edit.

| Permission | MASTER_ADMIN | SYSTEM_ADMIN | STUDENT |
|---|---|---|---|
| `STUDENT_READ` | yes | yes | own record only |
| `STUDENT_CREATE` | yes | yes | no |
| `STUDENT_UPDATE` | yes | yes | own permitted fields only |
| `STUDENT_STATUS` | yes | yes | no |
| `STUDENT_ISSUE_ACTIVATION_CODE` | yes | yes | no |
| `STUDENT_DELETE` | yes | **no** | no |
| `ADMIN_MANAGE` | yes | no | no |
| `AUDIT_READ` | yes | no | no |

**Delete convention (must generalize to all future phases):** every delete-capable endpoint, in this phase and every later one, is gated by a distinct `*_DELETE` permission, and no role except `MASTER_ADMIN` is ever granted a `*_DELETE` permission. A `SYSTEM_ADMIN` calling any delete endpoint must receive `403 Forbidden`, enforced server-side — never rely on the client UI hiding a button.

**Master Admin singularity:** no permission set, no endpoint, and no combination of the two may create, edit role of, deactivate, or delete a `MASTER_ADMIN` record. This must hold even if called by another `MASTER_ADMIN`.

Write the authorization mechanism so a future module (e.g. courses in Phase 2) can register new permissions like `COURSE_DELETE` without changing the enforcement engine itself.

---

## 10. Client Requirements

### 10.1 Web (`web`, Next.js)
Screens/behavior required in this phase, no more:
- Login page.
- Protected route wrapper; unauthenticated users redirected to login.
- Role-aware navigation: delete-capable actions/buttons are hidden entirely for `SYSTEM_ADMIN`, not merely disabled.
- Admin accounts page: list, create (`MASTER_ADMIN` only — hide for `SYSTEM_ADMIN`), activate/deactivate, and re-issue activation code, with the code shown once in a copyable UI element.
- Student list page: search + pagination.
- Student create page/form: on success, display the one-time activation code prominently and clearly (it will not be retrievable again through this flow after leaving the screen).
- Refresh token handled via httpOnly cookie set by the backend; the web app must send `X-Client-Type: web` on auth calls and must not attempt to read or store the refresh token itself.
- API client built from or validated against the OpenAPI spec.
- All user-facing strings externalized into a translation resource (Bangla + English), not hard-coded inline, even though only these few screens exist yet — this sets the pattern for every later phase.

### 10.2 Mobile (`mobile`, Flutter, Android target)
Screens/behavior required in this phase, no more:
- Login screen.
- Register screen (self-registration) with an email-verification step/prompt.
- Activate-account screen: phone + code + new password + confirm password.
- Forgot-password flow: request code/link, then a reset screen accepting the code + new password.
- Placeholder home screen reachable only when authenticated.
- Dev/prod build flavors with a configurable API base URL per flavor.
- Refresh token and access token stored via secure storage (e.g. `flutter_secure_storage`); mobile client sends `X-Client-Type: mobile`.
- HTTP layer: an interceptor that, on a `401`, attempts one token refresh and retries the original request; if refresh fails, clears stored tokens and routes to login.
- All user-facing strings externalized (Bangla + English) with a font capable of rendering Bangla script correctly.

---

## 11. Testing Requirements

Backend integration tests must run against a **real PostgreSQL instance via Testcontainers**, not an in-memory substitute (e.g. H2), because production behavior must match. Every item below is a required, automatable test case, not a manual QA step.

**Authentication**
- [ ] Login succeeds with phone identifier and with email identifier.
- [ ] Login fails with wrong password; error message is generic (does not reveal which part was wrong or whether the account exists).
- [ ] Refresh returns a new token pair and invalidates the previously presented refresh token.
- [ ] Presenting an already-rotated (reused) refresh token is rejected, and it revokes every token in that `family_id`.
- [ ] Expired or malformed access/refresh tokens are rejected.
- [ ] Logout revokes the presented refresh token; that token can no longer refresh.
- [ ] A user with `status = INACTIVE` cannot log in and cannot refresh.
- [ ] Repeated login failures trigger the rate limit/lockout.
- [ ] `/auth/activate` with a valid, unused, unexpired code sets the password and moves status to `ACTIVE`.
- [ ] `/auth/activate` rejects: expired code, already-used code, wrong code, and a code that has exceeded its failed-attempt cap.
- [ ] A user with `status = PENDING_ACTIVATION` cannot log in.
- [ ] `/auth/forgot-password` returns an identical response shape for an existing and a non-existing email.
- [ ] `/auth/reset-password` with a valid code sets a new password and revokes all existing refresh tokens for that user.
- [ ] `/auth/verify-email` confirms a valid code; a resend path issues a new code.
- [ ] No admin-facing endpoint accepts or returns a plaintext password field for any user other than the caller's own `change-password` call — assert this across the full admin API surface.
- [ ] `/auth/change-password` requires and validates the caller's current password before accepting a new one.
- [ ] Calling auth endpoints with `X-Client-Type: web` returns the refresh token only as a cookie; with `X-Client-Type: mobile`, only in the response body.

**Authorization**
- [ ] `SYSTEM_ADMIN` receives `403` from every delete endpoint (`DELETE /admins/{id}`, `DELETE /students/{id}`).
- [ ] `SYSTEM_ADMIN` receives `403` from `/admins` management endpoints (create/status) and `AUDIT_READ`-gated endpoints.
- [ ] `MASTER_ADMIN` can successfully soft-delete a student and a System Admin.
- [ ] A `STUDENT` cannot read another student's record, and cannot access any `/admins` or `/students` admin-only endpoint.
- [ ] No reachable endpoint can create, edit the role of, deactivate, or delete a `MASTER_ADMIN` record, tested against every relevant endpoint.

**Data integrity**
- [ ] Soft-deleted users are excluded from list endpoints and cannot authenticate.
- [ ] A soft-deleted user's phone number and email can be reused by a newly created account (proves the partial unique index works as intended).
- [ ] Creating a user with a duplicate active phone or email is rejected with a clear error code.
- [ ] Every delete, status change, activation-code issuance, and password reset produces a corresponding `audit_log` row with correct `actor_user_id`, `action`, and `entity_id`.

---

## 12. Phase Exit Criteria

Do not consider Phase 1 done until every item below is verifiable, not just believed true:

1. A `MASTER_ADMIN`, created only via bootstrap, logs into `web`, creates a `SYSTEM_ADMIN` and a `STUDENT` (the admin-created path).
2. The created `STUDENT` activates their account on `mobile` via `/auth/activate`, choosing their own password, and can subsequently log in and remain logged in across app restarts (token persistence + refresh working).
2a. **Separately**, a different prospective student self-registers directly via `/auth/register` on `mobile` with no admin involved, and can log in. This path must work independently of item 1 — it is not gated by any admin action.
3. Every delete endpoint rejects `SYSTEM_ADMIN` with `403`, proven by automated tests.
4. A deactivated user cannot log in or refresh.
5. Delete actions, activation-code issuance, and password resets all appear in `audit_log`.
6. No endpoint anywhere in the system allows any actor to set or view another user's password — proven by test, not by code review alone.
7. Full forgot-password → reset-password flow works end to end via the configured `EmailSender`.
8. All Section 11 tests pass in CI against a real PostgreSQL instance.
9. The OpenAPI spec served by the running application matches the implemented endpoints exactly.
10. No secret, credential, or key exists anywhere in version control.

If any item fails, the phase is not complete regardless of how much other work has been done.
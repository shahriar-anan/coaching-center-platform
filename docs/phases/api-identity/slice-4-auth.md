# api-identity slice 4 — auth

Stage: `api-identity` (Phase 1). Slice 4 only. Admin and student management routes were not started.

Date: 2026-09-25

## Done

- `/api/v1` auth routes: register, activate, login, refresh, logout, change-password, forgot-password, reset-password, verify-email, resend, and `GET /me`.
- Access tokens are 15-minute JWTs with user id, role, and expiry. Permissions are still resolved from the role, not stored in the token.
- Refresh tokens last 30 days, are stored as hashes, and rotate inside a `family_id`. Reuse of an already-rotated token revokes that family.
- `X-Client-Type: web` returns the refresh token only as an httpOnly Secure SameSite cookie. `mobile` returns it only in the JSON body.
- Passwords are BCrypt hashes. One-time codes and refresh tokens are stored as SHA-256 hashes. `EmailSender` sends verification and reset messages and does not log the code unless a dev-only flag is on.

## Tested

| Check | Result |
|---|---|
| `AuthLoginIT` — phone and email login, generic failure, inactive and pending users, lockout | Passed |
| `TokenIT` — rotation, reuse, expiry, logout, web cookie, mobile body, inactive refresh | Passed |
| `ActivationIT` — valid, expired, used, wrong, and exhausted codes | Passed |
| `PasswordIT` — identical forgot-password response, reset revokes refresh tokens, change-password checks the current password | Passed |
| `EmailVerificationIT` — confirm and resend | Passed |

Ran against local PostgreSQL 17.9.

## Failed

The first token-reuse run left the rest of the family usable. Rotation had already marked the old token revoked, so the reuse check never ran and the family revocation rolled back with the error. The check now runs first, and the revocation commits in its own transaction. The rerun passed.

## Next

Slice 5 is admin and student management. `PasswordIT` now checks that those routes reject a password field.

# api-identity slice 5 — admin and student management

Stage: `api-identity` (Phase 1). Slice 5 only. Bootstrap, audit writes, and OpenAPI were not started.

Date: 2026-09-25

## Done

- Admin-created students and System Admins are `PENDING_ACTIVATION` with no password hash. The raw activation code is returned once and is not emailed. Re-issue invalidates unused prior codes.
- Create requests that include a password field are rejected with `PASSWORD_NOT_ACCEPTED`.
- Soft delete, status changes, lists, student read/update, and activation-code re-issue follow the permission matrix. A student can read or update only their own record.
- `DELETE /admins/{id}` requires `ADMIN_DELETE`, granted only to `MASTER_ADMIN`. Section 8.2 lists that route as `ADMIN_MANAGE`. The delete convention in Section 9 wins; `docs/phases/phase-1.md` was not edited.
- Targeting a Master Admin (create, status, delete, or activation-code re-issue) returns `403` `MASTER_ADMIN_PROTECTED`.
- `GET /api/v1/audit-log` exists so `AUDIT_READ` can be enforced. It does not write audit rows.
- Dev mail stays on `LoggingEmailSender` and does not log codes unless the dev flag is on. Staging and prod use `SmtpEmailSender`, selected by profile, from `EMAIL_HOST` and `EMAIL_FROM`. No provider SDK.

## Tested

| Check | Result |
|---|---|
| `PasswordIT` — forgot-password, reset, change-password, and password rejected on `/students` and `/admins` | Passed |
| `AuthorizationIT` — System Admin deletes, admin create/status, and audit read are `403`; Master Admin soft-deletes; student scope; Master Admin cannot be mutated | Passed |
| `DataIntegrityIT` — soft-deleted users hidden and cannot log in; phone and email reusable after soft delete; duplicate active phone or email | Passed |

Ran against local PostgreSQL 17.9.

## Failed

None.

## Next

Slice 6 is bootstrap: one `MASTER_ADMIN` from environment variables, and no overwrite if one already exists. `docs/phases/phase-1.md` Section 11 still says the suite runs via Testcontainers.

# api-identity slice 3 — authorization

Stage: `api-identity` (Phase 1). Slice 3 only. Auth endpoints and admin or student routes were not started.

Date: 2026-09-25

## Done

- One role-to-permission map. Checks use permission names, not raw roles.
- Grants match the phase matrix. A student may read or update only their own record. A System Admin cannot delete, manage admins, or read the audit log.
- Only `MASTER_ADMIN` can receive a `*_DELETE` permission. A later module can register a new permission, including `COURSE_DELETE`, without changing the checker.
- A Master Admin account cannot be created, changed, deactivated, or deleted. That failure uses code `MASTER_ADMIN_PROTECTED`.

## Tested

| Check | Result |
|---|---|
| `AuthorizationTest` — matrix, delete-permission rule, permission check from the current role, Master Admin guard | Passed |

## Failed

None.

## Next

Slice 4 is authentication: register, activate, login, refresh, logout, and password and email flows. The HTTP cases in `AuthorizationIT` wait until the admin and student endpoints exist in slice 5.

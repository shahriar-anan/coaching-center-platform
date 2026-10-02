# Phase 1 status

Status: complete. Phone exit passed on 2026-10-01.

The binding spec is [phase-1-scope.md](phase-1-scope.md). This file records that the phase finished and where each stage's plan, required tests, and slice notes live. It does not repeat the spec.

Where the spec says "Section 9" for the test list, use Section 11.

## Stages

```text
api-identity → mobile-login-spike → web-admin → mobile-auth-screens
```

| Stage | App | Plan | Tests | Progress |
|---|---|---|---|---|
| `api-identity` | `api/` | [plan](../../../api/.cursor/plans/api-identity.md) | [tests](../../../api/.cursor/tests/api-identity.md) | [progress](../../../api/.cursor/progress/api-identity/) |
| `mobile-login-spike` | `mobile/` | [plan](../../../mobile/.cursor/plans/mobile-login-spike.md) | [tests](../../../mobile/.cursor/tests/mobile-login-spike.md) | [progress](../../../mobile/.cursor/progress/mobile-login-spike/) |
| `web-admin` | `web/` | [plan](../../../web/.cursor/plans/web-admin.md) | [tests](../../../web/.cursor/tests/web-admin.md) | [progress](../../../web/.cursor/progress/web-admin/) |
| `mobile-auth-screens` | `mobile/` | [plan](../../../mobile/.cursor/plans/mobile-auth-screens.md) | [tests](../../../mobile/.cursor/tests/mobile-auth-screens.md) | [progress](../../../mobile/.cursor/progress/mobile-auth-screens/) |

`web-admin` was built before `mobile-login-spike` because that order was requested. The sequence above is the one the exits were written against.

## Exits

- `api-identity`: Section 11 passes against real PostgreSQL, and CI runs that suite. GitHub Actions run on `03709f2` succeeded on 2026-10-01.
- `mobile-login-spike`: login works end to end from the app. A wrong field, status code, or flow is fixed in `api` and its tests before further UI.
- `web-admin`: a Master Admin logs in, creates a System Admin and a student, and the activation code is shown once. The System Admin activates on `web`, sets their own password, and reaches the admin shell. Pending-student re-issue was checked in the browser.
- `mobile-auth-screens`: an admin-created student activates and stays signed in across a restart, a different student self-registers and signs in with no admin, and forgot-password then reset-password works. Passed on a physical Android phone on 2026-10-01.

## Slice records

### api-identity

| Slice | Finished |
|---|---|
| [1 Foundation](../../../api/.cursor/progress/api-identity/slice-1-foundation.md) | App shell, error shape, Flyway ready |
| [2 Schema](../../../api/.cursor/progress/api-identity/slice-2-schema.md) | Identity tables |
| [3 Authorization](../../../api/.cursor/progress/api-identity/slice-3-authorization.md) | Roles and permissions |
| [4 Auth](../../../api/.cursor/progress/api-identity/slice-4-auth.md) | Login, refresh, register, activate, reset |
| [5 Admin and student management](../../../api/.cursor/progress/api-identity/slice-5-admin-student-management.md) | Admin and student endpoints |
| [6 Bootstrap](../../../api/.cursor/progress/api-identity/slice-6-bootstrap.md) | One Master Admin from the environment |
| [7 OpenAPI and audit](../../../api/.cursor/progress/api-identity/slice-7-openapi-audit.md) | OpenAPI and audit log |
| [8 CI](../../../api/.cursor/progress/api-identity/slice-8-ci.md) | API tests on every push |

### mobile-login-spike

| Slice | Finished |
|---|---|
| [1 Client](../../../mobile/.cursor/progress/mobile-login-spike/slice-1-client.md) | Login call, secure tokens, English and Bangla |
| [2 Login screen](../../../mobile/.cursor/progress/mobile-login-spike/slice-2-login.md) | Identifier, password, student-only gate |

### web-admin

| Slice | Finished |
|---|---|
| [1 Client foundation](../../../web/.cursor/progress/web-admin/slice-1-client-foundation.md) | API client and catalogs |
| [2 Session](../../../web/.cursor/progress/web-admin/slice-2-session.md) | Login and cookie refresh |
| [3 Admin accounts](../../../web/.cursor/progress/web-admin/slice-3-admin-accounts.md) | System Admin list and create |
| [4 Students](../../../web/.cursor/progress/web-admin/slice-4-students.md) | Student list and create |
| [5 System Admin activation](../../../web/.cursor/progress/web-admin/slice-5-system-admin-activation.md) | Activate on the web and reach the admin shell |
| [Student activation re-issue](../../../web/.cursor/progress/web-admin/student-activation-reissue.md) | Pending rows can show a new code once. Checked in the browser |

### mobile-auth-screens

| Slice | Finished |
|---|---|
| [1 Session and flavors](../../../mobile/.cursor/progress/mobile-auth-screens/slice-1-session.md) | Refresh, logout, home, dev and prod flavors |
| [2 Register](../../../mobile/.cursor/progress/mobile-auth-screens/slice-2-register.md) | Self-registration and email verification |
| [3 Activate](../../../mobile/.cursor/progress/mobile-auth-screens/slice-3-activate.md) | Admin-created student sets a password |
| [4 Forgot password](../../../mobile/.cursor/progress/mobile-auth-screens/slice-4-forgot-password.md) | Request and reset |
| [Phone exit](../../../mobile/.cursor/progress/mobile-auth-screens/phone-exit.md) | Passed on a physical Android phone, 2026-10-01 |

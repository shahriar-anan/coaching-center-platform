# Phase 1 progress

The specification is [phase-1.md](phase-1.md). Stage order is in [.cursor/plans/phase-1.md](../../.cursor/plans/phase-1.md).

This folder is the progress record. One directory per stage. Slice notes keep the "Next" line from the day they were written. The table below is the current status.

| Stage | Status | Record |
|---|---|---|
| `api-identity` | Complete | [api-identity](api-identity/) |
| `mobile-login-spike` | Complete | [mobile-login-spike](mobile-login-spike/) |
| `web-admin` | Complete | [web-admin](web-admin/) |
| `mobile-auth-screens` | Complete | [mobile-auth-screens](mobile-auth-screens/) |

Phone exit for both mobile stages: [phone-exit.md](mobile-auth-screens/phone-exit.md), passed 2026-10-01.

## api-identity

| Slice | Finished |
|---|---|
| [1 Foundation](api-identity/slice-1-foundation.md) | App shell, error shape, Flyway ready |
| [2 Schema](api-identity/slice-2-schema.md) | Identity tables |
| [3 Authorization](api-identity/slice-3-authorization.md) | Roles and permissions |
| [4 Auth](api-identity/slice-4-auth.md) | Login, refresh, register, activate, reset |
| [5 Admin and student management](api-identity/slice-5-admin-student-management.md) | Admin and student endpoints |
| [6 Bootstrap](api-identity/slice-6-bootstrap.md) | One Master Admin from the environment |
| [7 OpenAPI and audit](api-identity/slice-7-openapi-audit.md) | OpenAPI and audit log |
| [8 CI](api-identity/slice-8-ci.md) | API tests on every push |

## mobile-login-spike

| Slice | Finished |
|---|---|
| [1 Client](mobile-login-spike/slice-1-client.md) | Login call, secure tokens, English and Bangla |
| [2 Login screen](mobile-login-spike/slice-2-login.md) | Identifier, password, student-only gate |

## web-admin

| Slice | Finished |
|---|---|
| [1 Client foundation](web-admin/slice-1-client-foundation.md) | API client and catalogs |
| [2 Session](web-admin/slice-2-session.md) | Login and cookie refresh |
| [3 Admin accounts](web-admin/slice-3-admin-accounts.md) | System Admin list and create |
| [4 Students](web-admin/slice-4-students.md) | Student list and create |
| [5 System Admin activation](web-admin/slice-5-system-admin-activation.md) | Activate on the web and reach the admin shell |
| [Student activation re-issue](web-admin/student-activation-reissue.md) | Pending rows can show a new code once. Checked in the browser |

## mobile-auth-screens

| Slice | Finished |
|---|---|
| [1 Session and flavors](mobile-auth-screens/slice-1-session.md) | Refresh, logout, home, dev and prod flavors |
| [2 Register](mobile-auth-screens/slice-2-register.md) | Self-registration and email verification |
| [3 Activate](mobile-auth-screens/slice-3-activate.md) | Admin-created student sets a password |
| [4 Forgot password](mobile-auth-screens/slice-4-forgot-password.md) | Request and reset |
| [Phone exit](mobile-auth-screens/phone-exit.md) | Passed on a physical Android phone, 2026-10-01 |

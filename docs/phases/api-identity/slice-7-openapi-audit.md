# api-identity slice 7 — OpenAPI and audit

Stage: `api-identity` (Phase 1). Slice 7 only. CI was not started.

Date: 2026-09-25

## Done

- Deletes, status changes, and activation-code issuance write an `audit_log` row with the acting user, action, and user id. A password reset writes the same row with a null actor.
- The audit repository still has no update or delete method.
- OpenAPI is generated from the controllers and limited to `/api/v1/**`. `/v3/api-docs` and Swagger UI are public.

## Tested

| Check | Result |
|---|---|
| `AuditLogIT` — delete, status change, activation-code issuance, and password reset | Passed |
| `AuditLogIT` — served OpenAPI operations match the implemented `/api/v1` routes | Passed |

Ran against local PostgreSQL 17.9.

## Failed

None.

## Next

Slice 8 is CI: `.github/workflows/api.yml` builds `api` and runs the Testcontainers suite on every push.

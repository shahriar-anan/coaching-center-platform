# api-identity slice 2 — schema

Stage: `api-identity` (Phase 1). Slice 2 only. Authorization and auth endpoints were not started.

Date: 2026-09-25

## Done

- Flyway `V1__identity.sql` creates `users`, `student_profiles`, `admin_profiles`, `refresh_tokens`, `one_time_codes`, and `audit_log`.
- Partial unique indexes on `users.phone` and `users.email` where `deleted_at` is null.
- Phones are constrained to `+8801XXXXXXXXX`. Timestamps are `timestamptz`.
- `audit_log` is immutable in the application, and the database rejects updates and deletes.
- `users` extends `BaseEntity`. The other five tables follow Section 6 and do not have `updated_at` or `deleted_at`.

## Tested

| Check | Result |
|---|---|
| `SchemaIT` — column sets, partial phone/email uniqueness, phone format, append-only `audit_log`, against local PostgreSQL 17.9 | Passed |
| Hibernate `ddl-auto=validate` on startup of that test | Passed |

## Failed

None.

## Next

Slice 3 is authorization: one role-to-permission map, deletes only for Master Admin.

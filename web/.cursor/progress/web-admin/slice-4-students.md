# Slice 4 — Students

Date: 2026-09-26

## Done

- `/students` list with `q` search and pagination for both admin roles.
- `/students/new` create form with one-time activation code and displayed `studentCode`.
- Delete control rendered only when `role === MASTER_ADMIN` (absent for System Admin).

## Tested

| Check | Result |
|---|---|
| `npm run build` | Passed |
| `npm run lint` | Passed |

## Failed

None.

## Stage exit (manual)

Run with API on `http://localhost:8080`, `WEB_ORIGIN=http://localhost:3000`, and bootstrap Master Admin credentials:

1. Master Admin logs in on `web`, creates a System Admin and a student; copy each activation code once.
2. System Admin logs in, searches students, and does not see admin nav or student delete.

Vitest cases from `web/.cursor/tests/web-admin.md` are not wired yet.

## Next

The browser exit later passed. The completion record is `docs/phases/phase-1/phase-1-status.md`.

# Slice 3 — Admin accounts

Date: 2026-09-26

## Done

- `/admins` list with pagination; nav link only for `MASTER_ADMIN`.
- `/admins/new` create form (`fullName`, `phone`, `email` only) with one-time `activationCode` in `ActivationCodePanel`.
- Row actions: activate/deactivate for `ACTIVE`/`INACTIVE` only; `PENDING_ACTIVATION` shows re-issue only (no status patch to `ACTIVE`).
- Re-issue and delete on the list page (Master Admin only).

## Tested

| Check | Result |
|---|---|
| `npm run build` | Passed |

## Failed

None.

## Next

Slice 4 — students list, search, create, Master-only delete.

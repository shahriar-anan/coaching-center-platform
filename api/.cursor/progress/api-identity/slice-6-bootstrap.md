# api-identity slice 6 — bootstrap

Stage: `api-identity` (Phase 1). Slice 6 only. Audit writes, OpenAPI, and CI were not started.

Date: 2026-09-25

## Done

- On startup, if no Master Admin exists, one `ACTIVE` account is created from `MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME`, and `MASTER_ADMIN_PASSWORD`. Only the password hash is stored.
- If a Master Admin already exists, startup does not create another and does not change the password.
- Missing bootstrap values fail startup only when no Master Admin exists.

## Tested

| Check | Result |
|---|---|
| `BootstrapIT` — one account, login with the initial password, a second run leaves the password hash unchanged | Passed |

Ran against local PostgreSQL 17.9.

## Failed

The first run did not start. `LoggingEmailSender` used a missing-bean check that skipped the only dev sender. The check was removed; the dev profile always registers that sender. The rerun passed.

## Next

Slice 7 is OpenAPI and audit writes for delete, status change, activation-code issuance, and password reset.

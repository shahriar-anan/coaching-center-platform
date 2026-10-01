# Phase 1 stage map

Source of truth for Phase 1 order, where each stage is built, and what done means. The binding product and technical spec is [docs/phases/phase-1.md](../../docs/phases/phase-1.md).

Where that spec says "Section 9" for the test list, use Section 11.

## Current stage

- id: `mobile-auth-screens`
- status: `complete`

`api-identity`, `web-admin`, `mobile-login-spike`, and `mobile-auth-screens` are complete. Shahriar passed the mobile phone exit on 2026-10-01: an admin-created student activates and stays signed in across a restart, a different student self-registers and signs in with no admin, and forgot-password then reset-password works. There is no later stage in this map.

## Stages

```text
api-identity → mobile-login-spike → web-admin → mobile-auth-screens
```

### api-identity

- Directory: `api/`
- Later plan: `api/.cursor/plans/api-identity.md`
- Build the foundation and identity system from the phase spec: Flyway schema, auth, roles, permissions, audit log, OpenAPI, and tests against real PostgreSQL (Testcontainers-managed in CI; an external local instance is an acceptable substitute for local dev).
- CI that builds `api` and runs those tests on every push lives in `.github/workflows/`. Plan that workflow with this stage. It is part of this stage's exit.
- Exit: the spec's Section 11 test list passes against real PostgreSQL, and CI runs that suite.

### mobile-login-spike

- Directory: `mobile/`
- Later plan: `mobile/.cursor/plans/mobile-login-spike.md`
- One login screen wired to the running API. This is a contract spike, not the full screen set.
- Exit: login works end to end from the app. If a field, status code, or flow is wrong, fix `api` and its tests before any further UI.

### web-admin

- Directory: `web/`
- Later plan: `web/.cursor/plans/web-admin.md`
- Starts only after `api-identity` exit checks pass.
- Build the admin screens in spec Section 10.1.
- Follow `web/AGENTS.md` and the Next.js docs in `web/node_modules/next/dist/docs/`.
- Exit: a Master Admin can log in, create a System Admin and a student, and the activation code is shown once. The System Admin activates on `web` with that phone and code, sets their own password, and reaches the admin shell.

### mobile-auth-screens

- Directory: `mobile/`
- Later plan: `mobile/.cursor/plans/mobile-auth-screens.md`
- Starts only after `api-identity` exit checks pass. Same start gate as `web-admin`.
- Build the remaining student screens in spec Section 10.2.
- Exit: admin-created activation and independent self-registration both work on the app, and a logged-in student stays logged in across restarts.

## Later plans

Do not write these files in the phase-map step. Write each one when that stage is planned:

- `api/.cursor/plans/api-identity.md`
- `mobile/.cursor/plans/mobile-login-spike.md`
- `web/.cursor/plans/web-admin.md`
- `mobile/.cursor/plans/mobile-auth-screens.md`

A component plan may add detail. It may not drop a phase exit check, reorder these stages, or pull in a later-phase domain.

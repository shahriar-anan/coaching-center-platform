# web-admin

Stage plan for the Phase 1 admin panel. The binding spec is [docs/phases/phase-1.md](../../../docs/phases/phase-1.md) Section 10.1. This plan adds order and client detail. It does not drop an exit check.

Required test cases are in [web-admin-tests.md](web-admin-tests.md).

## Progress

Shahriar manually tested the running app. Status: success. Date: 2026-09-26.

| Slice | Status |
|---|---|
| 1 Client foundation | Done |
| 2 Session | Done |
| 3 Admin accounts | Done |
| 4 Students | Done. Pending-student re-issue included. |
| 5 System Admin activation | Done. Browser pass succeeded. |

The Vitest cases in [web-admin-tests.md](web-admin-tests.md) are not written. The stage exit above was checked in the browser.

The stage map still lists `mobile-login-spike` before `web-admin`. This stage was built first because Shahriar asked for the web plan first.

Do not build courses, batches, enrollments, content, exams, attendance, payments, library, notices, push delivery, file storage, analytics, Google sign-in, SMS OTP, or deployment automation. Do not add a web CI workflow in this stage. Student self-registration, activation, forgot-password, and reset stay on `mobile` (spec Section 10.2).

If a field, status code, or cookie behavior is wrong, fix `api` and its tests before changing the client to compensate.

## Already in `web/`

Next.js 16.3.6, React 19.2.8, TypeScript, Tailwind 4, ESLint. App Router under `web/src/app`. Path alias `@/*`. No test runner, no API client, no locale catalogs. `page.tsx` and `layout.tsx` are the create-next-app starter. `node_modules` is not installed yet.

Before writing screen code, install dependencies and read the Next.js guides in `web/node_modules/next/dist/docs/`. This release renamed `middleware.ts` to `proxy.ts`. Route protection, when used, exports `proxy` from `src/proxy.ts`. Heed deprecation notices in those guides.

## Contract the screens call

Base path `/api/v1`. Dev CORS origin is `http://localhost:3000`. The browser calls the API origin with `credentials: 'include'`.

| Call | Body | Result |
|---|---|---|
| `POST /auth/activate` | `phone`, `code`, `password` | `204`. No token. |
| `POST /auth/login` | `identifier`, `password` | `accessToken`, `expiresInSeconds`. `refreshToken` is omitted. `Set-Cookie: refresh_token` |
| `POST /auth/refresh` | empty | New access token. Cookie rotates. |
| `POST /auth/logout` | empty | `204`. Cookie cleared. |
| `GET /me` | | `id`, `phone`, `email`, `emailVerifiedAt`, `role`, `status`, `fullName`, `studentCode` |
| `GET /admins` | `page`, `size` | Page envelope |
| `POST /admins` | `fullName`, `phone`, `email` | `201` and `activationCode` once |
| `PATCH /admins/{id}/status` | `status`: `ACTIVE` or `INACTIVE` | `204` |
| `POST /admins/{id}/activation-code` | | `{ activationCode }` once |
| `DELETE /admins/{id}` | | `204` |
| `GET /students` | `q`, `page`, `size` | Page envelope |
| `POST /students` | `fullName`, `phone`, `email` | `201`, `activationCode`, `studentCode` |
| `DELETE /students/{id}` | | `204` |

Auth calls send `X-Client-Type: web`. Later calls send `Authorization: Bearer`.

The refresh cookie is HttpOnly, Secure, SameSite=Strict, Path=`/api/v1/auth`. The browser stores it for the API origin. `proxy.ts` and Server Components on the Next origin cannot see it. Page loads restore a session by calling `POST /auth/refresh` from the browser.

Page query `page` is zero-based. The envelope is `content`, `page`, `size`, `totalElements`, `totalPages`.

Errors are `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId`. Map `code` to a catalog string. Phone values may be `01XXXXXXXXX` or `+8801XXXXXXXXX`; the API normalizes to `+8801XXXXXXXXX`.

OpenAPI is the running app's `/v3/api-docs`.

`ADMIN_MANAGE` and every `*_DELETE` permission belong to `MASTER_ADMIN` only. `SYSTEM_ADMIN` can list and create students. `STUDENT` does not use this app.

## Slice 1 — Client foundation

Replace the starter page, starter copy, and the Latin-only Geist setup.

- `NEXT_PUBLIC_API_BASE_URL` defaults to `http://localhost:8080` in dev. Do not commit secrets.
- Generate TypeScript types from `/v3/api-docs`. Screen calls use those types. A check fails when the generated file drifts from the spec.
- One fetch wrapper sets the base URL, the bearer access token when one is in memory, and `credentials: 'include'` on `/auth/login`, `/auth/refresh`, and `/auth/logout`.
- Map API `code` values to catalog keys. Keep `requestId` available for support text.
- Catalogs: `en` and `bn`, covering every user-facing string on these screens. English is the language on first load. A control switches to Bangla and back. Load a `next/font` face that renders Bangla.
- Keep Tailwind. Do not add a component library.

## Slice 2 — Session

- Login page: identifier and password. Header `X-Client-Type: web`.
- Keep `accessToken` in memory only. Do not write it, or `refresh_token`, to `localStorage`, `sessionStorage`, or `document.cookie`.
- Do not send `refreshToken` in a JSON body.
- On load, call `POST /auth/refresh` with credentials and `X-Client-Type: web`. Failure leaves the user on login.
- On `401`, refresh once and retry the original request once. A second `401` clears memory and redirects to login.
- Logout calls `POST /auth/logout`, then clears memory.
- Unauthenticated visitors to a protected route land on login.
- After `GET /me`, `STUDENT` is signed out and stays on login. `MASTER_ADMIN` and `SYSTEM_ADMIN` enter the shell.
- `proxy.ts` may only redirect. It is not the authorization check. The API remains the authority.

## Slice 3 — Admin accounts

Visible to `MASTER_ADMIN` only. Omit the admin nav item for `SYSTEM_ADMIN`. Do not render a disabled link.

- Paginated list.
- Create form: `fullName`, `phone`, `email`. No password control, and no password property in the JSON.
- On success, show `activationCode` in one copyable element. Leaving the screen drops it. Do not store it.
- Status control sends `ACTIVE` or `INACTIVE` only. A `PENDING_ACTIVATION` row shows that state and the re-issue action. Do not mark a pending account `ACTIVE` from this control.
- Re-issue shows the new code once, in the same copyable element.
- Delete is present on this page. The page is already Master Admin only.

## Slice 4 — Students

Visible to both admin roles.

- List with search (`q`) and pagination.
- Create form matches the admin create rules. Show `activationCode` once. `studentCode` may stay on the row; it is not the one-time code.
- A `PENDING_ACTIVATION` row offers re-issue for both admin roles. The new code is shown once, then dropped when the screen closes. Active and inactive rows have no re-issue action.
- Delete is in the DOM for `MASTER_ADMIN` only. For `SYSTEM_ADMIN` the control is absent, not disabled.

Do not add student edit or student status. Spec Section 10.1 does not list them. Do not add a student activation or registration screen.

## Slice 5 — System Admin activation

Public page linked from login. No session required.

- Fields: phone, activation code, password, confirm password. No name field.
- Confirm password must match before submit. The API body is `phone`, `code`, and `password` only. Password length is 8 to 72 characters.
- `POST /auth/activate` returns `204` and no token. On success, `POST /auth/login` with that phone, the new password, and `X-Client-Type: web`, then open the admin shell.
- A `SYSTEM_ADMIN` lands on the student list, with no admin-accounts navigation and no delete control.
- If `GET /me` returns `STUDENT`, clear the session and show that the account is active and this panel is for administrators. The code is already used. Student activation stays on `mobile`.
- Do not change the activate API. Do not email the code.

## Stage exit

A Master Admin logs into `web` against the running API, creates a System Admin and a student, and each activation code is shown once. The System Admin activates on `web` with that phone and code, sets their own password, and reaches the student list without admin management or a delete control. A System Admin can also search students.

When a slice's checks finish, write `docs/phases/web-admin/slice-<number>-<name>.md` before the next slice. Record what was done, what was tested and the result, any failure and why, and what is next.

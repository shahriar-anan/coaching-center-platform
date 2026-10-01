# web-admin tests

Required cases for spec Section 10.1 and the `web-admin` stage exit. Test code is written during implementation, not as part of planning.

Run component tests with Vitest and React Testing Library. Mock `fetch`. Do not boot `api` for these tests. Do not add a GitHub workflow in this stage.

The stage exit is a browser pass against the running API, listed at the end. If the cookie or a response field is wrong there, fix `api` and its tests first.

Shared setup:

- Catalogs loaded so assertions can target translated text.
- A memory-only token holder. Tests fail if the implementation writes `accessToken` or `refresh_token` to `localStorage`, `sessionStorage`, or `document.cookie`.
- Roles come from a mocked `GET /me`.

## Session

- An unauthenticated visit to a protected route redirects to login.
- Login sends `X-Client-Type: web`, `credentials: 'include'`, and JSON `{ identifier, password }` with no `refreshToken`.
- A successful login keeps `accessToken` in memory only.
- `INVALID_CREDENTIALS` renders the generic translated login error.
- App load calls `POST /auth/refresh` with `X-Client-Type: web`, credentials, and no refresh-token body.
- A failed refresh leaves the user on login.
- The first `401` on an authenticated call refreshes once and retries once. A second `401` clears the token and redirects to login.
- Logout calls `POST /auth/logout` and clears the in-memory token.
- Role `STUDENT` does not enter the admin shell.
- `SYSTEM_ADMIN` has no admin-accounts navigation.
- `MASTER_ADMIN` has admin-accounts navigation.

## Admin accounts

- The create form has name, phone, and email, and no password input.
- The create JSON has no `password`, `passwordHash`, `newPassword`, or `currentPassword`.
- A `201` shows `activationCode` in a copyable element.
- After leaving that screen and returning, that code is not shown.
- Re-issue shows the new code once.
- The status control submits only `ACTIVE` or `INACTIVE`.
- A `PENDING_ACTIVATION` row does not offer a control that submits `ACTIVE`.

## Students

- The list request sends `q`, `page`, and `size`.
- An empty list and an error response render translated strings.
- Create shows `activationCode` once and follows the same no-password rule.
- A `PENDING_ACTIVATION` student row shows re-issue for both `MASTER_ADMIN` and `SYSTEM_ADMIN`.
- Re-issue shows the new code once. An `ACTIVE` row has no re-issue control.
- A `MASTER_ADMIN` student row contains a delete control.
- A `SYSTEM_ADMIN` student row does not contain a delete control.

## System Admin activation

- The page is reachable from login without a session.
- The form has phone, code, password, and confirm password, and no name field.
- Mismatched confirm password does not call the API.
- Submit sends `POST /auth/activate` with `{ phone, code, password }` only.
- After `204`, the page calls `POST /auth/login` with that phone, the new password, `X-Client-Type: web`, and credentials.
- A `SYSTEM_ADMIN` session opens the student list and hides admin-accounts navigation and delete.
- A `STUDENT` result clears the session and shows that the account is active and this panel is for administrators.

## Catalogs and OpenAPI

- Every user-facing string on these screens exists in both the English and Bangla catalogs.
- First load renders the English login title.
- Switching to Bangla changes the rendered login title. Switching back restores English.
- Generated types include login, refresh, logout, `/me`, `/admins`, and `/students` operations used by the screens.

## Browser exit

Against the running API, with dev CORS origin `http://localhost:3000`:

- Master Admin logs in.
- Master Admin creates a System Admin. The activation code is visible once and can be copied. It is gone after leaving the screen.
- Master Admin creates a student. The activation code is visible once.
- System Admin opens the activation page, enters that phone, code, and a new password, and lands on the student list.
- That System Admin does not see admin management or a delete control, and can search students.
- The browser stores `refresh_token` as an HttpOnly cookie on the API origin. The Next app never reads it.

A missing cookie, a refresh token in the JSON body, or a wrong field stops the stage. Fix `api` and its tests before further UI.

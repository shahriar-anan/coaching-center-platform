# mobile-auth-screens tests

Required cases for spec Section 10.2 and Phase 1 exit items 2 and 2a. Widget tests are written during implementation. Run them with `flutter test`. Mock the HTTP client. Do not boot `api` for widget tests. Do not add a GitHub workflow.

Section 11 stays the API suite. Do not re-implement those cases here.

The stage exit is a run on Shahriar's physical Android phone against the running API. The agent runs `flutter test` and does not install a system image, create an AVD, or launch an emulator. For email codes in that phone run, set `DEV_EXPOSE_EMAIL_CODES=true` in `api/.env` for that run only and read the code from the API log.

## Session

- A stored refresh token causes `POST /auth/refresh` with `X-Client-Type: mobile` and JSON `{ refreshToken }` on launch.
- A successful refresh stores the new pair and opens the home.
- A failed refresh clears both tokens and opens login.
- The first `401` refreshes once and retries once. A second `401` clears both tokens and opens login.
- Logout sends the stored refresh token with the bearer access token, then clears storage.
- The home is not in the tree when signed out.
- Killing the process and opening the app again restores a valid session.

## Register

- The form has name, phone, email, password, and confirm password.
- Mismatched passwords do not call the API.
- Submit sends `{ fullName, phone, email, password }` and no confirm field.
- A `201` shows the verification step and does not block a later login.
- Verify sends `{ code }` to `POST /auth/verify-email`.
- Resend sends `{ email }` to `POST /auth/verify-email/resend`.
- `DUPLICATE_PHONE` and `DUPLICATE_EMAIL` render translated errors.

## Activate

- The form has phone, code, password, and confirm password, and no name field.
- Submit sends `{ phone, code, password }` only.
- After `204`, the app calls login with that phone and the new password.
- `INVALID_CODE`, `CODE_EXPIRED`, `CODE_ALREADY_USED`, and `CODE_ATTEMPTS_EXCEEDED` render distinct translated errors.

## Forgot password

- The request sends `{ email }`.
- The success text is the same for an address the test double treats as missing and one it treats as present.
- Reset sends `{ code, password }` only.
- After reset, the stored refresh token is cleared and the student must sign in again.

## Catalogs and flavors

- Every user-facing string on these screens exists in both the English and Bangla catalogs.
- First launch is English.
- The `dev` flavor default base URL is `http://10.0.2.2:8080` and cleartext is allowed.
- The `prod` flavor does not allow cleartext.

## Device exit

Shahriar runs these on a physical phone with `--dart-define=API_BASE_URL=http://<lan-ip>:8080`. The agent does not launch an emulator.

- Create a student in the admin panel. Activate that account in the app with the shown phone and code. Sign in. Force-stop the app and open it again. The home is shown without typing the password.
- Register a different student in the app. No admin step. Sign in before verifying email. Verify email with the code from the dev API log.
- Request a password reset for that student, set a new password with the logged code, and confirm the old refresh token cannot refresh.

## Phase 1 checklist

Do not call Phase 1 done until every row is true.

| Exit | Where it is proved |
|---|---|
| 1. Master Admin creates a System Admin and a student on `web`. The System Admin activates on `web`. | Already passed in `web-admin` |
| 2. That student activates on `mobile`, signs in, and stays signed in across a restart | Device exit above |
| 2a. A different student self-registers on `mobile` and signs in with no admin | Device exit above |
| 3–6, 8–10. Deletes, lockout, audit, password invariant, Section 11 in CI, OpenAPI, no secrets in git | Already passed in `api-identity` |
| 7. Forgot-password then reset-password through `EmailSender` | Device exit above, with the dev expose flag |

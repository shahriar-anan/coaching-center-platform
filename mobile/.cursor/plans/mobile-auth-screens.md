# mobile-auth-screens

Stage plan for the remaining Phase 1 student screens. The binding spec is [phase-1-scope.md](../../../docs/phases/phase-1/phase-1-scope.md) Section 10.2. This plan adds order only. It does not drop an exit check.

Required test cases are in [mobile-auth-screens.md](../tests/mobile-auth-screens.md).

Start only after the `mobile-login-spike` exit checks pass. Keep the login client, secure storage, catalogs, and Bangla font from that stage.

Do not build courses, batches, enrollments, content, exams, attendance, payments, library, notices, push delivery, file storage, analytics, Google sign-in, SMS OTP, or deployment automation. Do not add a mobile CI workflow. Do not add admin screens. Student accounts are not created or activated on `web`.

If a field, status code, or token placement is wrong, fix `api` and its tests before changing the client to compensate.

## Contract the screens call

Base path `/api/v1`. Header `X-Client-Type: mobile` on login, refresh, and logout. Refresh and logout send `refreshToken` in the JSON body. They do not use cookies.

| Call | Body | Result |
|---|---|---|
| `POST /auth/register` | `fullName`, `phone`, `email`, `password` | `201` and the current user. Status `ACTIVE`. No tokens. An `EMAIL_VERIFY` code is emailed. Login does not wait for `emailVerifiedAt`. |
| `POST /auth/verify-email` | `code` | `204` |
| `POST /auth/verify-email/resend` | `email` | Same message whether or not the email exists |
| `POST /auth/activate` | `phone`, `code`, `password` | `204`. No token. |
| `POST /auth/forgot-password` | `email` | Same message whether or not the email exists |
| `POST /auth/reset-password` | `code`, `password` | `204`. Revokes all of that user's refresh tokens. |
| `POST /auth/login` | `identifier`, `password` | `accessToken`, `expiresInSeconds`, `refreshToken` in the JSON body |
| `POST /auth/refresh` | `refreshToken` | New pair. The presented refresh token is dead. |
| `POST /auth/logout` | `refreshToken` | `204`. Requires `Authorization: Bearer`. |
| `GET /me` | | Current user and profile. Requires the bearer token. |

Password length is 8 to 72 characters. Confirm password is checked on the device and is not sent.

Errors use the same `code` mapping as the spike. Add catalog strings for `INVALID_CODE`, `CODE_EXPIRED`, `CODE_ALREADY_USED`, `CODE_ATTEMPTS_EXCEEDED`, `DUPLICATE_PHONE`, and `DUPLICATE_EMAIL`.

Dev email codes are hidden unless `DEV_EXPOSE_EMAIL_CODES=true`. For a local manual run, set that in `api/.env` and restart the API. The API log then prints the verification or reset code. Do not commit that flag as `true`. Do not show the code in the app. Activation codes are not emailed. They come from the admin panel.

## Slice 1 — Session and flavors

- Android product flavors `dev` and `prod`. `dev` defaults to `http://10.0.2.2:8080` and allows cleartext. `prod` takes `API_BASE_URL` from the build and does not allow cleartext. Do not invent a production host.
- On launch, if a refresh token is stored, call `POST /auth/refresh` and open the placeholder home. Failure clears both tokens and opens login.
- On `401`, refresh once and retry the original request once. A second `401` clears both tokens and opens login.
- Logout sends the stored refresh token, then clears storage and opens login.
- Replace the spike's signed-in confirmation with the placeholder home. The home calls `GET /me` and shows the student's name. It is reachable only when authenticated.

## Slice 2 — Register

- Form: full name, phone, email, password, confirm password.
- After `201`, show an email-verification step: code field, submit, and resend. The student can leave that step and log in without verifying.
- Resend and a missing account show the same generic message.

## Slice 3 — Activate

- Form: phone, activation code, password, confirm password. No name field.
- The phone is the one the admin entered. The code is the one shown once in the admin panel.
- After `204`, sign in with that phone and the new password and open the home.

## Slice 4 — Forgot password

- Request screen: email. Always show the same translated message after the call.
- Reset screen: code, new password, confirm password.
- After `204`, the old refresh tokens are dead. The student signs in with the new password.

## Stage exit

Shahriar runs these on a physical Android phone against the running API. The agent does not install a system image, create an AVD, or launch an emulator. Phone command: `flutter run --dart-define=API_BASE_URL=http://<lan-ip>:8080`.

- An admin-created student activates in the app, chooses a password, signs in, and is still signed in after the app process is killed and opened again.
- A different student registers with no admin involved, can log in before verifying email, and can verify email with the dev-exposed code.
- Forgot-password then reset-password works, and the previous refresh token cannot refresh.

When a slice's checks finish, write `mobile/.cursor/progress/mobile-auth-screens/slice-<number>-<name>.md` before the next slice. Record what was done, what was tested and the result, any failure and why, and what is next.

## Phase 1 done

This stage's exit covers spec Section 12 items 2 and 2a. Items 1 and 3 through 10 are already met by `api-identity` and `web-admin`. Do not mark Phase 1 done until items 2 and 2a pass on Shahriar's physical phone. The checklist is in [mobile-auth-screens.md](../tests/mobile-auth-screens.md). The pass is recorded in [phase-1-status.md](../../../docs/phases/phase-1/phase-1-status.md).

# mobile-login-spike

Stage plan for the Phase 1 login spike. The binding spec is [docs/phases/phase-1.md](../../../docs/phases/phase-1.md) Section 2 step 2 and Section 10.2. This plan adds order only. It does not drop an exit check.

Required test cases are in [mobile-login-spike-tests.md](mobile-login-spike-tests.md).

Implement this stage before [mobile-auth-screens.md](mobile-auth-screens.md). Do not build register, activation, forgot-password, email verification, or the full placeholder home in this stage. A signed-in confirmation is enough to prove login.

Do not build courses, batches, enrollments, content, exams, attendance, payments, library, notices, push delivery, file storage, analytics, Google sign-in, SMS OTP, or deployment automation. Do not add a mobile CI workflow.

If a field, status code, or token placement is wrong, fix `api` and its tests before changing the client to compensate.

## Already in `mobile/`

Flutter counter sample. Dart SDK `^3.13.4`. Android application id `com.coachingcenter.mobile`. No HTTP client, no secure storage, no flavors, no catalogs. `lib/main.dart` is the demo counter. `android/app/src/main/AndroidManifest.xml` does not allow cleartext HTTP.

## Contract this stage calls

Base path `/api/v1`. Header `X-Client-Type: mobile` on login.

| Call | Body | Result |
|---|---|---|
| `POST /auth/login` | `identifier`, `password` | `accessToken`, `expiresInSeconds`, `refreshToken` in the JSON body. No `Set-Cookie`. |

`identifier` is phone or email. Phone may be `01XXXXXXXXX` or `+8801XXXXXXXXX`. The API stores `+8801XXXXXXXXX`.

Errors are `code`, `message`, optional `fieldErrors`, `timestamp`, `requestId`. Map `code` to a catalog string. Wrong password is `INVALID_CREDENTIALS` and must stay generic.

Dev API base URL defaults to `http://10.0.2.2:8080`. That value stays in the app. It is not a reason to install or start an emulator. Shahriar tests on a physical phone with `--dart-define=API_BASE_URL=http://<lan-ip>:8080`.

The debug build must allow cleartext HTTP to that dev host. Do not allow cleartext in a release build.

## Slice 1 — Client

Replace the counter demo.

- One HTTP wrapper. Login sends `X-Client-Type: mobile` and `Content-Type: application/json`. It does not send cookies and does not read `Set-Cookie`.
- Store `accessToken` and `refreshToken` with `flutter_secure_storage`. Do not write them to shared preferences, a file, or logs.
- English and Bangla catalogs for the strings on this screen. English is the language on first launch. A control switches to Bangla and back.
- Use Noto Sans Bengali so Bangla glyphs render.

## Slice 2 — Login screen

- Fields: identifier and password.
- Success opens a signed-in confirmation that shows the account is signed in. That screen is not the Section 10.2 home. The next stage replaces it.
- Failure shows the catalog string for the API `code`.
- `STUDENT` may stay signed in. `MASTER_ADMIN` and `SYSTEM_ADMIN` are signed out and told this app is for students.

## Stage exit

Shahriar runs the app on a physical Android phone against the running API, once with a phone and once with an email. The refresh token is present only in the JSON body and is stored in secure storage. The agent does not install a system image, create an AVD, or launch an emulator for this exit.

When a slice's checks finish, write `docs/phases/mobile-login-spike/slice-<number>-<name>.md` before the next slice. Record what was done, what was tested and the result, any failure and why, and what is next.

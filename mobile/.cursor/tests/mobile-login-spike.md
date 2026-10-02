# mobile-login-spike tests

Required cases for the login spike. Widget tests are written during implementation. Run them with `flutter test`. Mock the HTTP client. Do not boot `api` for widget tests. Do not add a GitHub workflow.

The stage exit is a run on Shahriar's physical Android phone against the running API. The agent runs `flutter test` and does not install a system image, create an AVD, or launch an emulator. Hand over `flutter run --dart-define=API_BASE_URL=http://<lan-ip>:8080`.

Shared setup:

- Catalogs loaded so assertions can target translated text.
- A fake secure store. Tests fail if a token is written anywhere else.

## Widget

- Login sends `POST /api/v1/auth/login`, header `X-Client-Type: mobile`, and JSON `{ identifier, password }`.
- The request does not send a refresh token and does not set a cookie.
- A success body stores `accessToken` and `refreshToken` from the JSON body.
- `INVALID_CREDENTIALS` renders the generic translated login error.
- First launch renders the English login title.
- Switching to Bangla changes that title. Switching back restores English.
- Role `STUDENT` reaches the signed-in confirmation.
- Role `MASTER_ADMIN` or `SYSTEM_ADMIN` stays on login with the students-only message.

## Device exit

Shahriar runs these on a physical phone. API on the host at port 8080. Phone base URL is `http://<lan-ip>:8080`. The in-app default `http://10.0.2.2:8080` stays in code and is not an emulator run.

- A student who already has a password signs in with a phone.
- The same student signs in with an email.
- The stored refresh token matches the JSON `refreshToken`. The response has no refresh cookie.
- A wrong password shows the generic error.
- A `PENDING_ACTIVATION` account cannot sign in.

A missing `refreshToken` in the JSON body, or a refresh cookie, stops the stage. Fix `api` and its tests before further UI.

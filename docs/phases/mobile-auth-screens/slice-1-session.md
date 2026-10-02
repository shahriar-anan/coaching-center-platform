# Slice 1 — Session and flavors

Date: 2026-09-30

## Done

- Android product flavors `dev` and `prod`. `dev` allows cleartext HTTP. `prod` does not. The debug manifest no longer forces cleartext onto every flavor.
- `dev` defaults to `http://10.0.2.2:8080`. `prod` has no built-in host. `--dart-define=API_BASE_URL=` overrides either flavor.
- A stored refresh token calls `POST /api/v1/auth/refresh` on launch with `X-Client-Type: mobile` and `{ refreshToken }`. Success stores the new pair and opens the home. Failure clears both tokens and opens login.
- `GET /me` shows the student's name. The first 401 refreshes once and retries once. A second 401 clears both tokens and opens login.
- Logout sends the stored refresh token with the bearer access token, then clears storage and opens login.
- The spike's signed-in confirmation is gone. The home is not built while signed out.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (30 tests) |
| `flutter analyze` | No issues |
| `dev` and `prod` debug manifest merge | Passed |

No emulator. The process-kill check in the widget suite is a new app launch with a stored refresh token.

## Failed

None.

## Next

Slice 2 — register. The phone exit is recorded in `phone-exit.md`.

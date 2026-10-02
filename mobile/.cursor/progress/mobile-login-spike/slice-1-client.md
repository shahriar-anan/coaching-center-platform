# Slice 1 — Client

Date: 2026-09-26

## Done

- Replaced the Flutter counter sample with an app shell that loads English and Bangla catalogs. English is the language on first launch. Noto Sans Bengali is the app font.
- Added an HTTP login call for `POST /api/v1/auth/login` with `X-Client-Type: mobile` and JSON `{ identifier, password }`. The refresh token is accepted only from the JSON body. A refresh cookie is rejected.
- Added `flutter_secure_storage` for the access token and the refresh token. Student sessions write both tokens there. `MASTER_ADMIN` and `SYSTEM_ADMIN` sessions are cleared.
- Debug builds allow cleartext HTTP. The release manifest does not.
- The emulator base URL defaults to `http://10.0.2.2:8080`. `--dart-define=API_BASE_URL=` overrides it.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (9 tests) |
| `flutter analyze` | Passed after the initializing-formal lint fix |

## Failed

None.

## Next

Slice 2 is the login screen. Later student screens and the phone exit are under `../mobile-auth-screens/`.

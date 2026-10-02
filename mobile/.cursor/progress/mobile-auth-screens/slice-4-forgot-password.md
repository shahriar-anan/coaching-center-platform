# Slice 4 — Forgot password

Date: 2026-09-30

## Done

- Request step sends `{ email }` and always shows the same catalog message.
- Reset step sends `{ code, password }` only. Confirm password stays on the device.
- After `204`, stored tokens are cleared and the student signs in again with the new password.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (30 tests, including forgot password) |
| `flutter analyze` | No issues |

## Failed

None.

## Next

Phone exit passed. See `phone-exit.md`.

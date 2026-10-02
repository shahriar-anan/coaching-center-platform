# Slice 3 — Activate

Date: 2026-09-30

## Done

- Activate form: phone, activation code, password, confirm password. No name field.
- Submit sends `{ phone, code, password }` only.
- After `204`, the app signs in with that phone and the new password and opens the home.
- `INVALID_CODE`, `CODE_EXPIRED`, `CODE_ALREADY_USED`, and `CODE_ATTEMPTS_EXCEEDED` each have their own catalog string.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (30 tests, including activate) |
| `flutter analyze` | No issues |

## Failed

None.

## Next

Slice 4 — forgot password. The phone exit is recorded in `phone-exit.md`.

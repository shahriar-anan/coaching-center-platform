# Slice 2 — Login screen

Date: 2026-09-26. Phone checks: 2026-10-01.

## Done

- Identifier and password. A student reaches a signed-in screen. A failure shows the catalog string for the API `code`.
- `STUDENT` stays signed in. `MASTER_ADMIN` and `SYSTEM_ADMIN` are signed out and told this app is for students.
- The signed-in confirmation was later replaced by the home screen in `mobile-auth-screens`.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed with the later student-screen suite (30 tests) |
| Phone: sign in with phone and with email, wrong password, pending account cannot sign in | Passed. See `../mobile-auth-screens/phone-exit.md` |

## Failed

None.

## Next

`mobile-auth-screens`.

# Slice 2 — Register

Date: 2026-09-30

## Done

- Register form: full name, phone, email, password, confirm password. Confirm password and the 8–72 length check stay on the device.
- Submit sends `{ fullName, phone, email, password }`.
- A `201` opens the email-verification step (code, submit, resend). The student can leave that step and sign in without verifying.
- Resend shows one catalog message. `DUPLICATE_PHONE` and `DUPLICATE_EMAIL` use their own catalog strings.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (30 tests, including register) |
| `flutter analyze` | No issues |

## Failed

None.

## Next

Slice 3 — activate. The phone exit is recorded in `phone-exit.md`.

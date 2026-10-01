# Phone exit

Date: 2026-10-01

Shahriar ran the app on a physical Android phone against the API at `http://192.168.68.114:8080`. No emulator.

## Tested

| Check | Result |
|---|---|
| Admin-created student cannot sign in before activation | Passed |
| Activate with the web code, then Home shows the student name | Passed |
| Force-stop and open again restores Home without the password | Passed |
| Same student signs in with phone and with email | Passed |
| Wrong password shows the generic error | Passed |
| A different student self-registers and signs in before email verification | Passed |
| Email verification code from the API log verifies | Passed |
| Forgot-password then reset-password; the old session is dead and the new password signs in | Passed |
| Master Admin is rejected and stays on login | Passed |

## Failed

None.

## Next

Phase 1 stage map items for `mobile-login-spike` and `mobile-auth-screens` are complete.

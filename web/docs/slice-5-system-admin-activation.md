# Slice 5 — System Admin activation

Date: 2026-09-26

## Done

- Public `/activate` page linked from login. Fields are phone, activation code, password, and confirm password. No name field.
- Mismatched passwords and a length outside 8–72 characters stay in the browser.
- Submit sends `POST /auth/activate` with `phone`, `code`, and `password` only. A `204` is followed by login with that phone and password.
- A System Admin opens the student list. A student result clears the session and says the account is active and this panel is for administrators.
- The activate API was not changed. The code is not emailed.

## Tested

| Check | Result |
|---|---|
| `npm run lint` | Passed |
| `npm run build` | Passed |
| Browser pass by Shahriar | Success |

## Next

None for this slice. Student activation stays on `mobile`. `web-admin` screen work is complete.

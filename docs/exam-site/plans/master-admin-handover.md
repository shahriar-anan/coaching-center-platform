# Master Admin handover

**Audience:** the agent that implements this, and Shahriar for the server slices. **Status:** written 25 October 2026. Shahriar answered D1–D4 the same day. No code yet.

## Goal

The client receives a Master Admin account that uses the client's real email. The password stays the one Shahriar uses today. The client changes the password and the phone himself after handover.

Shahriar confirmed on 25 October 2026 that nothing in the VPS database is needed. The database is rebuilt empty, and the existing bootstrap creates the account from the environment. [../decisions.md](../decisions.md#master-admin-at-delivery) option 2 already describes this path.

## What the code allows today

Read from the code on branch `catalog-acquisition`.

| Need | Today | Change in this plan |
|---|---|---|
| Account created with the real email | `MasterAdminBootstrap` creates the Master Admin from `MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME`, and `MASTER_ADMIN_PASSWORD`, only when none exists. | None. Server slices only. |
| Client changes the password | `POST /api/v1/auth/change-password` needs only the current password. No code is mailed. | Website page only. |
| Client changes the phone | `POST /api/v1/me/phone` fills an **empty** phone only (Google sign-up). The Master Admin has a phone, so it returns `409 PHONE_ALREADY_SET`. `decisions.md` says this call changes the phone. That line is wrong. | New API call and a website page. |
| Client verifies the email | `POST /api/v1/auth/verify-email/resend` and `verify-email` work for any role. The bootstrap leaves the email unverified. | Website page only. |
| Email and name | No call changes them. | None. The environment values are final, so pick them carefully. |
| Forgot password | Refused for the Master Admin by design. | None. A lost Master Admin password needs a server-side fix. Tell the client. |

## Decisions for Shahriar

Answered by Shahriar on 25 October 2026.

| # | Question | Answer |
|---|---|---|
| D1 | When is the database rebuilt? | Now, 25 October 2026, so the client can test today. A second rebuild on 29 October is allowed and would wipe everything the client enters during the test. |
| D2 | Who can change their own phone? | Every signed-in account that has a password. A Google-only account with no password keeps `POST /me/phone`. |
| D3 | The Master Admin display name | `MASTER ADMIN`. It changes only through another rebuild. |
| D4 | The email | The client's address, given by Shahriar. It goes only into `/etc/coaching-api.env`. It changes only through another rebuild. |
| D5 | Seed after the rebuild? | Open. With `SEED_ENABLED=false` the client sees no exam, because catalog authoring screens do not exist yet. With one seeded start the client gets the practice exam, plus the seed System Admin and seed student. |

## Slice order

Because of D1, the server slices 4–8 run first, today, with the jar already on the box. Slices 1–3 follow and reach the box with the next deploy, so the client can change the phone from then on. Slice 9 runs after slice 3. The server slices are on the VPS. Each server slice waits for Shahriar's approval, and its evidence goes into a new report `../reports/master-admin-handover.md` with no secret values, like [../reports/vps-staging-deploy.md](../reports/vps-staging-deploy.md).

| # | Slice | Where |
|---|---|---|
| 1 | API: change own phone | `api` |
| 2 | Website: Account page for staff | `web` |
| 3 | Build and deploy the jar and website | PC, then VPS |
| 4 | Collect the handover values | Shahriar and the client |
| 5 | Final backup | VPS, then PC |
| 6 | Rebuild the database and clear the files | VPS |
| 7 | Environment and first start | VPS |
| 8 | Remove the password from the environment | VPS |
| 9 | Handover check with the client | Browser |

## Slice 1 — API: change own phone

`POST /api/v1/auth/change-phone`, beside `change-password` in `AuthController`. No Flyway migration.

**Request** `{ "currentPassword": "…", "phone": "+8801XXXXXXXXX" }`. Both `@NotBlank`.

**Rules**, in `AuthService.changePhone`, `@Transactional`:

1. Load the signed-in user. Missing returns `401 UNAUTHENTICATED`.
2. No password hash, or the password does not match, returns `400 CURRENT_PASSWORD_INVALID`. The phone stays the same.
3. `PhoneNumbers.normalize` the phone. A bad format returns its existing error.
4. If the number equals the current phone, return `200` with `MeView` and write nothing.
5. Another account already using that number returns `409 DUPLICATE_PHONE`. Catch `DataIntegrityViolationException` on `saveAndFlush` and return the same code, as `setPhone` does.
6. Save the phone. Write `AuditRecorder.PHONE_CHANGED` (new constant) for the user.
7. Send a "phone number changed" notice to the account email through `sendQuietly`. It says the number changed and does not contain the old or new number. A mail failure does not undo the change. Add `sendPhoneChanged(String to)` to `EmailSender`, `SmtpEmailSender`, `LoggingEmailSender`, and the capturing test senders.
8. Refresh tokens stay valid. The password did not change.

**Response** `200` with `AuthService.MeView`, the same body as `POST /me/phone`.

**Master Admin:** allowed. Changing their own phone is the self-service path `decisions.md` already promises. No other account can change another user's phone.

**Docs:** add the call to [../current-api.md](../current-api.md). Correct the phone line in [../decisions.md](../decisions.md#master-admin-at-delivery) to name `POST /api/v1/auth/change-phone`.

**Tests:** `ChangePhoneIT`, extending `PostgresIntegrationTest`. Each write case asserts the status, the body, and the database row.

- A student changes the phone. `200`, the new phone in the body and in `users`, one `PHONE_CHANGED` audit row, one captured notice to the account email.
- The Master Admin changes the phone. Login with the new phone works, and login with the old phone returns the invalid-credentials error.
- Wrong current password. `400 CURRENT_PASSWORD_INVALID`, phone unchanged, no audit row, no mail.
- A number used by another account. `409 DUPLICATE_PHONE`, phone unchanged.
- A bad format such as `+88017123` or `12345`. Rejected, phone unchanged. The local form `01712345678` is normalized to `+8801712345678`, as at registration.
- The same number as now. `200`, no audit row, no mail.
- A Google-only account with no password. `400 CURRENT_PASSWORD_INVALID`.
- No token. `401`.
- Add the route to the list in `AuditLogIT`.

Run `ChangePhoneIT`, `PasswordIT`, `GoogleSignInIT`, and `AuditLogIT`, then the full `mvn test` on its own, because the suite crashes when other work runs beside it on this PC. `BookOrderIT` and `CoursePurchaseIT` may fail on a reused test database. That is the known pair and not this change.

## Slice 2 — Website: Account page for staff

Route `/account` under `web/src/app/(admin)/`, linked from the user menu or nav in `web/src/components/admin-shell.tsx` for every staff role. The student website gets its own account page in step 6. Do not add one here.

Three panels, all copy through `web/src/i18n/messages/en.ts` and `bn.ts`:

1. **Profile.** Name, email, phone, role, and whether the email is verified, from `GET /api/v1/me`. Name and email are read only. For the Master Admin, a line says they cannot be changed.
2. **Email verification**, shown while the email is unverified. "Send code" calls `POST /auth/verify-email/resend` with the account email. A code field calls `POST /auth/verify-email`. On success the profile shows verified.
3. **Change password.** Current, new, and confirm fields. Calls `POST /auth/change-password`, which already exists in `web/src/lib/api/client.ts`. The new password needs 8 to 72 characters, and the confirm field must match.
4. **Change phone.** New phone and current password. Calls the slice 1 route through a new `client.ts` function. On success the profile shows the new number.

Errors show the API `message` beside the form. A wrong password shows the `CURRENT_PASSWORD_INVALID` text, and a duplicate number shows `DUPLICATE_PHONE`.

**Checks:** `npm run lint`, `npm test`, `npm run build`. Component tests with mocked `fetch`, without booting `api`:

- The profile renders the `/me` values.
- Change password: success clears the form. A wrong current password shows the error and keeps the fields. A mismatched confirm does not call `fetch`.
- Change phone: success updates the shown phone. `409` shows the duplicate message.
- Verification: the panel hides when the email is verified. Send code then a correct code shows verified.

Shahriar does the browser pass.

## Slice 3 — Build and deploy

Same method as [../plans/vps-staging-deploy.md](vps-staging-deploy.md) slices 0, 4 and 6. Build the jar and the standalone website on the PC with the Linux `sharp` binaries, then check the SHA-256 on the box. Keep the previous jar and website as `.prev`. This slice can go before or after slices 4–9.

## Slice 4 — Collect the handover values

Shahriar gets these from the client. They go only into `/etc/coaching-api.env`, never into Git or this folder.

- `MASTER_ADMIN_EMAIL`: the client's real email (D4).
- `MASTER_ADMIN_NAME`: the final display name (D3).
- `MASTER_ADMIN_PHONE`: keep today's value. The client changes it on `/account` after handover. The format is `+8801` and nine digits.
- `MASTER_ADMIN_PASSWORD`: today's Master Admin password, typed in by Shahriar. It is not in the env file now. It was removed after the first boot on 23 October.

## Slice 5 — Final backup

Even though the data is not needed, keep one copy until the client signs off.

- `sudo -u postgres pg_dump -Fc coaching` to `/var/backups/coaching/coaching-pre-handover-<date>.dump`, then copy it to `F:\exam-vps-backups\`. Check it with `pg_restore --list` and compare the SHA-256 on both sides.
- `tar -C /var/lib/coaching-api -czf … files` to the same two places.

## Slice 6 — Rebuild the database and clear the files

1. Read the current owner and settings first: `\l coaching`, plus the role in `DB_USERNAME`. The new database must use the same owner, encoding, and locale.
2. `systemctl stop coaching-api`. The website can stay up. Its API calls fail until slice 7.
3. As `postgres`: `DROP DATABASE coaching;` then `CREATE DATABASE coaching OWNER <that role> …` with the settings from step 1. `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` stay as they are.
4. Move the contents of `/var/lib/coaching-api/files` into `/var/backups/coaching/files-pre-handover-<date>/`. Keep the directory itself with its owner and mode.

## Slice 7 — Environment and first start

1. Back up `/etc/coaching-api.env` to `.prev-handover`.
2. Set `MASTER_ADMIN_EMAIL` and `MASTER_ADMIN_NAME`, keep `MASTER_ADMIN_PHONE`, and add `MASTER_ADMIN_PASSWORD`. `SEED_ENABLED` follows D5. If the seed runs, set it to `true` for this one start and back to `false` in slice 8. Keep `640 root:deploy`. No CR, no quotes, no spaces around `=`.
3. `systemctl start coaching-api`. In the log: profile `staging`, Flyway applies every version to the latest, no error, and no password in the log.
4. Database: Flyway at the latest version, all successful. `users` has exactly one row, role `MASTER_ADMIN`, status `ACTIVE`, the new email. `admin_profiles.full_name` is the new name. Without the seed, there are no other users, orders, or catalog rows. With the seed, there is also one `SYSTEM_ADMIN`, one `STUDENT`, and the published practice module and exam, and no orders.

## Slice 8 — Remove the password from the environment

1. Delete the `MASTER_ADMIN_PASSWORD` line.
2. `systemctl restart coaching-api`. It must start. The bootstrap skips because a Master Admin exists.
3. `grep -c MASTER_ADMIN_PASSWORD /etc/coaching-api.env` returns `0`. Delete the `.prev-handover` copy too, because it holds the password.

## Slice 9 — Handover check with the client

1. Sign in on `/login` with the phone and today's password, then with the new email. Both work.
2. `/account` shows the new name and email, unverified.
3. Send the verification code. The client reads it from the inbox and enters it. The profile shows verified. This also proves mail reaches the client.
4. The client changes the password on `/account`. Sign in with the new password works, and the old one fails. The "password changed" mail arrives.
5. The client changes the phone on `/account` if he wants another number. Sign in with the new phone works. The "phone changed" mail arrives.
6. Tell the client: the email and the name cannot be changed. Forgot password does not work for this account. Keep the password safe.

## Out of this plan

- Changing the email or the name of an existing account.
- A forgot-password path for the Master Admin.
- The student account page (step 6).
- Switching to the `prod` profile, live SSLCommerz, and the `symamlaw.com` cutover. Those follow [../todays-goals.md](../todays-goals.md) section 5.

## Exit

- `ChangePhoneIT` and the full suite are green, apart from the known pair.
- Web lint, tests, and build pass.
- The VPS has one Master Admin with the client's email and name, no `MASTER_ADMIN_PASSWORD` in the environment, and the client has verified the email and changed the password.
- `decisions.md` and `current-api.md` are corrected, and the report is written.

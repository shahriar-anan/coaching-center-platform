# Master Admin handover — report

Running record for [../plans/master-admin-handover.md](../plans/master-admin-handover.md). No secret values.

## Slices 4–8 — Database rebuild with the client's Master Admin

Done 25 October 2026, about 08:00–08:35 Asia/Dhaka (02:00–02:35 UTC). Approved by Shahriar, who edited `/etc/coaching-api.env` himself.

**Before.** `coaching-api`, `coaching-web`, `nginx`, and `postgresql` active. Database `coaching`, owner `coaching`, `UTF8`, `C.UTF-8`, libc provider. Users: 1 `MASTER_ADMIN`, 1 `SYSTEM_ADMIN`, 2 `STUDENT`. `/var/lib/coaching-api/files` held 0 files, so nothing was moved.

**Backup (slice 5).** `/var/backups/coaching/coaching-pre-handover-20261025.dump` (`pg_dump -Fc`, `600`), 128,726 bytes, 49 table-data entries in `pg_restore --list`. Copied to `F:\exam-vps-backups\`. SHA-256 on both sides `c290e4cb0cef14d7cf1bea71b37889a65dc5eff0a9e195590eb5b075456e38c1`.

**Rebuild (slice 6).** `coaching-api` stopped. `DROP DATABASE coaching WITH (FORCE)`, then `CREATE DATABASE coaching OWNER coaching ENCODING UTF8 LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8' TEMPLATE template0`. Zero tables after create. The website stayed up.

**Environment (slice 7).** Shahriar set `MASTER_ADMIN_EMAIL` to the client's address, set `MASTER_ADMIN_PHONE` to a new number, kept `MASTER_ADMIN_NAME=Master Admin`, and added `MASTER_ADMIN_PASSWORD` (today's password). `SEED_ENABLED=false`. Format check: no duplicate names, no CR, no trailing spaces, no spaces around `=`, no quotes, no blank values. `640 root:deploy`. The password was checked by length and character class only, never printed.

**First start (slice 7).** Profile `staging`. Flyway applied 36 migrations, all successful. Started in 13.1 s, 0 restarts, 2 WARN lines (SpringDoc, as before). The jar is unchanged: commit `fea6d12`.

Database after the start: one user, `MASTER_ADMIN`, `ACTIVE`, the client's email, phone `+8801521339606`, `admin_profiles.full_name` `Master Admin`, email not verified. No modules, no orders.

**Password removed (slice 8).** Shahriar deleted the `MASTER_ADMIN_PASSWORD` line. `grep -c` returns 0. Restart: profile `staging`, started in 12.9 s, 0 restarts, still one `MASTER_ADMIN`. The bootstrap skipped as expected. `/etc/coaching-api.env.prev-handover` deleted. `/etc/coaching-api.env.prev` from the staging deploy is still there.

**From the PC over HTTPS.** Public categories 200, `/api/v1/me` 401, `/login` 200.

## Slices 1 and 3 — Change own phone, API deploy

Done 25 October 2026, about 08:30–08:50 Asia/Dhaka. The website page (slice 2) is deferred at Shahriar's request.

- `POST /api/v1/auth/change-phone` in `AuthController` and `AuthService.changePhone`. New `AuditRecorder.PHONE_CHANGED` and `EmailSender.sendPhoneChanged`. No Flyway version.
- Tests against local PostgreSQL 17 (`coaching_center_test`, via `TEST_DB_*`): `ChangePhoneIT` 9, `PasswordIT` 4, `GoogleSignInIT` 18, `AuditLogIT` 2, `SmtpEmailSenderTest` 8, `LoggingEmailSenderTest` 2, `ActivationSendFailureIT` 1, all green. Full suite 301 run, 299 pass. Only `BookOrderIT` and `CoursePurchaseIT` fail, the known reused-database pair.
- Jar built from the working tree: `02346ac`, this change, and the A8 purchase-notice work (`V38`), which was in the tree but not committed. 74,193,154 bytes, SHA-256 `aa3a61fdaf49442499b536eb31073e80f641516e6d6dc174b343f36177ff65e2`, matched on the box. The previous jar is kept as `/opt/coaching-api/api.jar.pre-phone`.
- Restart: Flyway applied `37 live exam order payment channel` and `38 exam mail purchase notice` from the A7 commit and the uncommitted A8 work. Now `38`, all successful. Started in 12.2 s, 0 restarts, 2 WARN (SpringDoc).
- Live check from the PC over HTTPS, with a script on a throwaway student (`shahriaranan18+phonecheck…@gmail.com`): register, login by phone and by email, no token 401, wrong password 400, the Master Admin's number 409, bad format 400, same number 200 unchanged, a new number 200, `/me` shows it, the new phone signs in, the old phone does not. 12 of 12 passed. On the box: one `PHONE_CHANGED` audit row, no mail send failure, no error.
- `MAIL_TEST_ENABLED` is still in `/etc/coaching-api.env`. The new jar ignores it.

## Still open

1. The Master Admin sign-in check. The live script runs it when given a file with the password. Not run yet.
2. Slice 2, the staff `/account` page. Until then the client changes the phone and the password through the API only, and cannot verify the email from the website.
3. Slice 9 with the client, after slice 3.
4. The catalog is empty. Shahriar adds the data seeds later today, before the UI work.
5. The name and email change only through another rebuild, possibly at the 29 October delivery. That rebuild wipes the client's test data.

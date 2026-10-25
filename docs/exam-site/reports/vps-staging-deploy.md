# VPS staging deploy — report

Running record for [../plans/vps-staging-deploy.md](../plans/vps-staging-deploy.md). One entry per slice. No secret values.

**Later the same day.** This report describes the box up to about 07:30 Asia/Dhaka on 25 October 2026. At 08:00 the database was rebuilt with only the client's Master Admin, so the seed system admin, the seed student, Shahriar's test student and the four test orders below no longer exist. At 08:45 the API moved to jar `aa3a61fd…` (A7 staff payments, own-phone change, A8 purchase email), Flyway 38. The current state is in [master-admin-handover.md](master-admin-handover.md).

## Slice 0 — Local gate

Done 25 October 2026, about 05:15 Asia/Dhaka.

- Commit `f785cef` on `catalog-acquisition` (after `60fb5c7`). Not pushed.
- API suite against local PostgreSQL 17 (`coaching_center_test`): 293 run, 291 pass. `BookOrderIT` and `CoursePurchaseIT` fail with 409 because their fixed `transactionRef` values collide with rows from earlier runs. Allowed by V1.
- `BootstrapIT` failed on a foreign key and also left the shared bootstrap Master Admin with a replaced password, which broke `ActivationMailIT`, `ActivationSendFailureIT`, `PasswordResetAudienceIT` and `InstructorDirectoryIT` in later runs. Fixed in test code only (`f785cef`). No application code changed.
- The first suite run crashed with a JVM out-of-memory error because web checks ran beside it. Run the suite alone on this PC.
- Jar: `api/target/api-0.0.1-SNAPSHOT.jar`, 74,175,309 bytes, SHA-256 `2b55314bac70631f3bac62fb958c8073b3e67865857fb1572f9e21d38ad58cf4`.
- Web: `npm run lint` 0 errors (2 old `<img>` warnings), `npm test` 41 passed, `npm run build` with `NEXT_PUBLIC_API_BASE_URL=https://srv2089893.hstgr.cloud` and `output: "standalone"`. The built JS calls the VPS. The `localhost:8080` string left in it is the unused fallback in `src/lib/config.ts`.
- Native modules: `@img/sharp-win32-x64` replaced by `@img/sharp-linux-x64` and `@img/sharp-libvips-linux-x64` 0.35.4. The standalone folder now holds only Linux `.node` and `.so` files.

## Slice 1 — Server check

Done 25 October 2026, about 05:20 Asia/Dhaka (23:20 UTC on 24 October). Read only.

Matches the 24 October audit: Ubuntu, 96 G disk with 3.8 G used, 7.8 GiB RAM, swap unused; `coaching-api`, `nginx`, `postgresql` and `fail2ban` active; Flyway `33|t`; one `MASTER_ADMIN` and no other users; Nginx config valid with only the `coaching` site enabled; 8080 on `127.0.0.1` only; Node not installed; `/var/run/reboot-required` present; no backup job.

Differences from the audit:

| Item | Audit (24 Oct) | Now |
|---|---|---|
| `coaching-api` active since | 2026-10-23 15:18 UTC | 2026-10-24 08:15 UTC, 0 restarts |
| Env names | no `EMAIL_USERNAME`, `EMAIL_PASSWORD` | both present (for the old jar). Removed in slice 3. |
| Jar | SHA-256 `59ae12c6…805a30` | same. Owner `deploy:deploy`, mode `640`. |
| Nginx HTTPS `server_name` | `srv2089893.hstgr.cloud` | `srv2089893.hstgr.cloud _` (also the catch-all) |

Checks made before slice 2, all read only:

- Flyway checksums `V1`–`V33` on the box are identical to the current migration files, so the new jar applies only `V34`–`V36` and will not fail validation.
- `pg_dump` 16.15 matches the server. The `coaching` DB is 9.4 MB. `/var/lib/coaching-api/files` holds 0 files.
- From the box, `smtp-relay.brevo.com:587` and `sandbox.sslcommerz.com:443` are reachable. Brevo's STARTTLS certificate lists `smtp-relay.brevo.com` in its SAN and verifies, which the API needs because it checks the server identity.
- `/opt/coaching-api` is `deploy:deploy 755`, so the new jar keeps the old owner and mode (`deploy:deploy 640`).

Env names present: `DB_PASSWORD DB_URL DB_USERNAME EMAIL_FROM EMAIL_HOST EMAIL_PASSWORD EMAIL_PORT EMAIL_USERNAME FILE_STORAGE_DIR JWT_SECRET MASTER_ADMIN_EMAIL MASTER_ADMIN_NAME MASTER_ADMIN_PHONE SERVER_ADDRESS SERVER_PORT SPRING_PROFILES_ACTIVE WEB_ORIGIN`. Non-secret values: `SPRING_PROFILES_ACTIVE=prod`, `WEB_ORIGIN=https://symamlaw.com`, `FILE_STORAGE_DIR=/var/lib/coaching-api/files`, `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=8080`.

## Slice 2 — Backup before change

Done 25 October 2026, about 05:21 Asia/Dhaka (23:21 UTC on 24 October). Approved by Shahriar.

- `/var/backups/coaching/` created (`700 root:root`).
- `coaching-pre-v36-20261025.dump` (`pg_dump -Fc`, 232,410 bytes, `600 root:root`). `pg_restore --list` reads it: 47 table-data entries. SHA-256 `f5912b7958d0897838583c1bf3b9bf598d2aa3772d6f9696cb3562a9cb2095ca`.
- Copied to `F:\exam-vps-backups\coaching-pre-v36-20261025.dump`. Same SHA-256.
- `/opt/coaching-api/api.jar.prev` (old jar, `deploy:deploy 640`) and `/etc/coaching-api.env.prev` (`640 root:deploy`). The env copy stays on the box only.
- No service was stopped or restarted.

## Slice 3 — Env file

Done 25 October 2026. Shahriar edited `/etc/coaching-api.env`. The agent changed one line, `SPRING_PROFILES_ACTIVE=prod` to `staging`, with his approval.

- Names: `DB_PASSWORD DB_URL DB_USERNAME FILE_STORAGE_DIR JWT_SECRET MAIL_FROM_ADDRESS MAIL_FROM_NAME MAIL_HOST MAIL_PASSWORD MAIL_PORT MAIL_TEST_ENABLED MAIL_USERNAME MASTER_ADMIN_EMAIL MASTER_ADMIN_NAME MASTER_ADMIN_PHONE SEED_ADMIN_EMAIL SEED_ADMIN_NAME SEED_ADMIN_PASSWORD SEED_ADMIN_PHONE SEED_ENABLED SEED_STUDENT_EMAIL SEED_STUDENT_NAME SEED_STUDENT_PASSWORD SEED_STUDENT_PHONE SERVER_ADDRESS SERVER_PORT SPRING_PROFILES_ACTIVE SSLCZ_BASE_URL SSLCZ_CALLBACK_BASE_URL SSLCZ_STORE_ID SSLCZ_STORE_PASSWORD WEB_ORIGIN`. No duplicates, no blank values, no `EMAIL_*`.
- Non-secret values: `SPRING_PROFILES_ACTIVE=staging`, `WEB_ORIGIN` and `SSLCZ_CALLBACK_BASE_URL` `https://srv2089893.hstgr.cloud`, `SSLCZ_BASE_URL=https://sandbox.sslcommerz.com`, `MAIL_HOST=smtp-relay.brevo.com`, `MAIL_PORT=587`, `MAIL_FROM_ADDRESS=no-reply@symamlaw.com`, `MAIL_TEST_ENABLED=false`, `SEED_ENABLED=false`.
- Format: no CR, no trailing spaces, no spaces around `=`, no quotes. Mode `640 root:deploy`.
- Open: both seed passwords are 8 characters. Shahriar to strengthen them before slice 7.
- `/etc/coaching-api.env.prev` still holds the old Gmail SMTP login. Remove it after slice 5 passes.

## Slice 4 — Deploy the jar

Done 25 October 2026, 05:51 Asia/Dhaka (23:51 UTC on 24 October). Approved by Shahriar.

- `/opt/coaching-api/api.jar` replaced after the SHA-256 matched slice 0 (`2b55314b…d38ad58cf4`). Owner `deploy:deploy`, mode `640`. `api.jar.prev` kept.
- `systemctl restart coaching-api`. Started in 16.2 s, `active (running)`, 0 restarts.
- Log: profile `staging`. Flyway applied `34 exam outbound mail`, `35 live exam deadline index` and `36 student notices`. DB check `36|t`. Two WARN lines only, both SpringDoc saying `/v3/api-docs` and `/swagger-ui.html` are enabled. Those answer on `127.0.0.1:8080` only: Nginx proxies just `/api/`, and `/api/v3/api-docs` returns 401 from outside. No SMTP key, API key or store password in the log.
- From the PC: `GET /api/v1/public/live-exam-categories` 200. `GET /api/v1/me` 401 `UNAUTHENTICATED`. `POST /api/v1/live-exam-orders` without a token 401.

## Slice 5 — Real mail check

Done 25 October 2026, about 06:10 Asia/Dhaka.

- First attempt: `POST /api/v1/auth/register` returned 201 for student `+8801744628303`, `blazeninja18@gmail.com` (random password, never shown), but the verification mail failed: `AuthenticationFailedException … failed to connect, no password specified?`. A manual SMTP AUTH from the box with the same `MAIL_USERNAME` and `MAIL_PASSWORD` returned `235 2.0.0 Authentication succeeded`, so the credentials and the server IP were fine.
- Cause: `SmtpEmailSender.JavaMailOutbound.send` called `transport.connect()` with no arguments. `JavaMailSenderImpl` keeps the host and credentials outside the session, so no real mail had ever been sent from `staging` or `prod`. Unit tests used a fake `OutboundMail` and never ran that class. Exam mail goes through the same method.
- Fix: commit `fea6d12` passes the host, port, username and password to `connect`. Suite 294 run, 292 pass (only the V1 pair fail). Jar SHA-256 `dfd9b0625de7e80a52ca9d6ccbb1e8ae3da6e3e4a702b4a6d61e0b523ef761f0`, installed at 06:03 Asia/Dhaka. The slice 4 jar is kept as `/opt/coaching-api/api.jar.slice4`. Started in 14 s, Flyway still `36|t`.
- `POST /api/v1/auth/verify-email/resend` 200. Shahriar received "Verify your email" in the inbox, from `SYMAM LAW <no-reply@symamlaw.com>`. `POST /api/v1/auth/verify-email` with that code 204. DB: `ACTIVE`, email verified.
- `POST /api/v1/auth/forgot-password` 200. Shahriar received "Reset your password" in the inbox, same sender. He keeps that code. He sets this account's password on `/reset-password` after slice 6.
- No send failure in the log after the fix. No key or code in the log. `MAIL_TEST_ENABLED` stayed `false`.
- Before slice 7, `SEED_STUDENT_EMAIL` must change, because this account now uses `blazeninja18@gmail.com` and the seed would skip its own student.
- After slice 5, with Shahriar's approval: `EMAIL_USERNAME` and `EMAIL_PASSWORD` removed from `/etc/coaching-api.env.prev`. The old Gmail login is no longer on the new box. The file keeps `640 root:deploy` and its other names.

## Slice 6 — Node, website, Nginx

Done 25 October 2026, about 06:40 Asia/Dhaka (00:40 UTC). Approved by Shahriar.

- Node `v22.23.4` from the NodeSource apt repo (`/etc/apt/keyrings/nodesource.gpg`, `/etc/apt/sources.list.d/nodesource.list`, `node_22.x nodistro`). Next 16.3.6 needs Node ≥ 20.9.
- Package: the standalone build plus `public` and `.next/static`, with the Linux `sharp` binaries. 15,970,301 bytes, SHA-256 `20c282e4b43dc919ffba6284786b9931713a1d75477d1c33bf60d61004c3e99f`. Unpacked to `/opt/coaching-web` (`deploy:deploy`, 53 MB).
- The build contains Windows paths (`outputFileTracingRoot`, `repoRoot`, `root` = `F:\coaching-center-platform\web`). Before Nginx was switched, every page was tested on `127.0.0.1:3000` on the box: all rendered, static assets loaded, no error in the log. They do not affect the runtime.
- `/etc/systemd/system/coaching-web.service`: user `deploy`, `node server.js`, `HOSTNAME=127.0.0.1`, `PORT=3000`, `NODE_ENV=production`, `Restart=on-failure`. Enabled and active, 0 restarts.
- Nginx `sites-available/coaching`: `location /` now proxies to `127.0.0.1:3000` with `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto` and `X-Forwarded-Host`. `client_max_body_size` raised from `90m` to `105m` (V5). `location /api/` and the Certbot lines are unchanged. Backup: `/var/backups/coaching/nginx-coaching-20261025T003816Z`. `nginx -t` ok, reloaded. A first edit attempt matched no text and stopped before writing anything.
- From the PC over HTTPS: `/login`, `/register`, `/forgot-password`, `/reset-password`, `/live-exams`, `/sandbox` and `/purchase/result` 200. Unknown path 404. A `/_next/static` asset 200. `/` 307 to `https://srv2089893.hstgr.cloud/students` (the current app sends `/` to the staff page). API categories 200, `/api/v1/me` 401. HTTP 301 to HTTPS. Ports 3000 and 8080 closed from outside.

## Slice 7 — Staging seed

Done 25 October 2026, about 06:55 Asia/Dhaka. Approved by Shahriar.

- Before the run: Shahriar changed both seed emails (no longer `blazeninja18@gmail.com`) and set 13-character seed passwords. Checks: no blank value, both seed emails and phones unused, the two emails different.
- `SEED_ENABLED=true`, restart, started in 16 s. Then `SEED_ENABLED=false`, restart, started in 14 s. Active, 0 restarts.
- DB: `MASTER_ADMIN` 1, `SYSTEM_ADMIN` 1, `STUDENT` 2 (the seed student and Shahriar's slice 5 account), all `ACTIVE`. "Staging practice exam", 10.00, published, in the published "Staging practice module".
- Public API: category "Staging practice", module "Staging practice module" (10.00), exam "Staging practice exam" (10.00).

## Slice 8 — Sandbox payment

Card payment done 25 October 2026, 06:49–06:50 Asia/Dhaka, by Shahriar in the browser as the seed student on `/sandbox`.

- Order `tranId=9f458ada58244cc198e1ebfd1e7e6e`, 10.00 BDT, `SUCCEEDED`. One grant row.
- Log, in order (UTC): 00:49:09 `sslcommerz session accepted`, `POST /api/v1/live-exam-orders` 201. 00:50:33 `sslcommerz validation accepted`, `live exam payment granted`. 00:50:34 `POST …/ipn status=200`. 00:50:40 `live exam payment already granted`, `POST …/return status=302`. 00:50:41 `GET /api/v1/live-exam-orders/status` 200.
- **S5 proof:** the IPN reached the box and granted access before the browser returned. The return then saw the existing grant and wrote nothing.
- The browser showed Paid on `/purchase/result`.
- `exam_outbound_mail`: `PAYMENT_RECEIPT` `SENT` at 00:50:33.
- Receipt delivery is not confirmed by this payment: the seed student's email is a placeholder address, so Brevo accepted the message but it will bounce. Receipt delivery is checked with the mobile-banking payment on Shahriar's real account.
- Finding: `SslCommerzProfileGuard` and `ProfileStartup` run as `ApplicationRunner`s, after Tomcat has started. A misconfigured process listens briefly and then exits. It does not exit "before serving" as `reports/sslcommerz-sandbox-runbook.md` says. The protection holds (the process cannot stay up). Test the guard on the box only with `coaching-api` stopped.
- Startup guard, run by the agent at about 07:00 Asia/Dhaka: `coaching-api` stopped, then the jar started once as `deploy` through `systemd-run` with the real env file plus the command-line argument `--SSLCZ_BASE_URL=https://securepay.sslcommerz.com`. Result: Tomcat started, then `SSLCZ_BASE_URL host is not allowed on the staging profile`, exit code 1 after 17 s. No secret in the output. The env file was not edited. The service was started again (16 s, 0 restarts, categories 200). About 40 s of API downtime.
- Seed emails replaced at Shahriar's request (the first ones were placeholders): seed student `+8801840464099` is now `shahriaranan18@gmail.com`, seed system admin `+8801500000001` is now `shahriaranany6@gmail.com`. One transaction, matched by phone and role, one row each. Both stay email-verified. Passwords unchanged. `SEED_STUDENT_EMAIL` and `SEED_ADMIN_EMAIL` in `/etc/coaching-api.env` updated to match (`640 root:deploy` kept). No restart needed: the API reads users from the DB, and `SEED_ENABLED` is `false`.

Unhappy paths and the second success, 25 October 2026, 07:07–07:16 Asia/Dhaka, by Shahriar on his own account (`+8801744628303`) in a private window. The student pages have no logout button.

| Order | Test | Result | Path |
|---|---|---|---|
| `5cdbdf0c645e41199a943d7c74b15e` | OTP page "Failed" | `FAILED`, no grant | IPN: `live exam payment rejected`, IPN 200. Return: `sslcommerz query failed`, 302. |
| `80ca1bfd37e745c1bff466c2cd88ea` | ✕ on the SSLCommerz page | `CANCELLED`, no grant | Two `session accepted` lines with the same `tranId` (Buy pressed again while pending, the order was reused). Return: `sslcommerz query cancelled`, 302. The `/sandbox` Cancel button was not needed. |
| `3b03e8ed5d10409d85958a1289e8da` | Success | `SUCCEEDED`, one grant | IPN first: `validation accepted`, `payment granted`, IPN 200. Return: `already granted`, 302. |

- Receipts: one `PAYMENT_RECEIPT` per successful order only. The 01:16:51 receipt arrived in Shahriar's inbox. Failed and cancelled orders send nothing.
- The sandbox store's Mobile Banking tab lists bKash, Nagad, Rocket, Upay, tap, OK Wallet, Cellfin, Islami Bank MCash and AB, on the same checkout page.
- The OTP page simulates outcomes with "Success", "Success with risk" and "Failed" buttons. It cannot test a real wrong OTP. That check is added to section 5 of `todays-goals.md` for the live store.
- Open decision: the API does not read SSLCommerz `risk_level`. A payment SSLCommerz marks risky ("Success with risk") still validates `VALID` and is granted at once. SSLCommerz advises merchants to review such payments before delivery. Shahriar decides whether to grant, hold, or flag them.
- Website gap: student pages have no logout. `logout()` exists in the session context and `POST /api/v1/auth/logout` in the API. Only the staff layout shows it.

## Slice 9 — Deferred

Shahriar deferred the nightly B2 backup on 25 October 2026. Until it exists, he pulls backups by hand to `F:\exam-vps-backups\`: `ssh exam-kvm 'sudo -u postgres pg_dump -Fc coaching' > …dump` and `ssh exam-kvm 'tar -C /var/lib/coaching-api -czf - files' > …tar.gz`, then `pg_restore --list` on the dump. Slice 9 (nightly encrypted off-server backup with a restore test) is still required before real client data, per client brief section 10. The pending reboot (`/var/run/reboot-required`) is also still open.

## Reboot

Done 25 October 2026, 07:20 Asia/Dhaka (01:20 UTC). Approved by Shahriar.

- Up since 2026-10-23 14:20 UTC with `/var/run/reboot-required` set. `coaching-api`, `coaching-web`, `nginx` and `postgresql` were all `enabled` before the reboot.
- Back on SSH about 25 s after the reboot command. Booted at 01:20:57 UTC on kernel `6.8.0-147-generic` (was `6.8.0-142`). `reboot-required` cleared.
- All of `coaching-api`, `coaching-web`, `nginx`, `postgresql`, `fail2ban`, `ufw` and `certbot.timer` came back active with no manual start, 0 restarts. API profile `staging`, started in 12.4 s. Two WARN lines (SpringDoc, as before). No error in the website log.

## Slice 10 — Close out

Final check after the reboot, 25 October 2026 about 07:22 Asia/Dhaka:

| Check | Result |
|---|---|
| Running jar | SHA-256 `dfd9b062…3ef761f0`, commit `fea6d12` |
| Website build | package SHA-256 `20c282e4…04c3e99f`, built from `f785cef` |
| Flyway | `36`, all successful |
| Env | `staging`, sandbox SSLCommerz host, `SEED_ENABLED=false`, `MAIL_TEST_ENABLED=false` |
| Orders | 2 `SUCCEEDED`, 1 `FAILED`, 1 `CANCELLED`, all test orders |
| Public | `/login`, `/register`, `/live-exams`, `/sandbox` and `/purchase/result` 200. API categories 200, `/api/v1/me` 401. HTTP 301 to HTTPS. |
| Exposure | Listening publicly on 22, 80 and 443 only. 3000, 5432 and 8080 bound to localhost, closed from outside. UFW allows OpenSSH and Nginx Full only. |
| Certificate | `srv2089893.hstgr.cloud`, expires 2027-01-21 |
| Disk | 4.3 G of 96 G used |

Left on the box from the deploy: `/opt/coaching-api/api.jar.prev` (the original jar), `/opt/coaching-api/api.jar.slice4`, `/etc/coaching-api.env.prev` (old env without the Gmail login), `/var/backups/coaching/` (pre-V36 dump and Nginx backup). Keep them until the client test is over.

Still open:

1. Nightly encrypted off-server backup (slice 9) before real client data. Manual pulls until then.
2. Student logout button and the `/` redirect to the staff page (website work).
3. Final website address. The old site keeps `symamlaw.com`. Then `WEB_ORIGIN`, `SSLCZ_CALLBACK_BASE_URL`, DNS, a certificate, and the live store's registered URL.
4. What to do with payments SSLCommerz marks risky. The API grants them today.
5. Live store: the client confirms bKash and Nagad are enabled. Run the live wrong-OTP and success check, with a refund from the merchant panel (`todays-goals.md` section 5).
6. `BookOrderIT` and `CoursePurchaseIT` fail on a reused test database (fixed `transactionRef`). Out of this delivery.
7. The API does not store the payment channel. Per Shahriar, the successes were a VISA sandbox card (`9f458ada…`, seed student) and bKash (`3b03e8ed…`, his account), so both a card and a mobile wallet passed through IPN validation and grant.
8. The IPN URL in the merchant panel was not needed for the sandbox: both successes received their IPN at the per-payment `ipn_url`. For the live store, the launch payment check confirms it (look for `…/ipn status=200`). If it is missing, the client asks SSLCommerz to clear the panel's IPN URL.
9. Risky payments: SSLCommerz `risk_level=1` marks fraud signals (card country versus IP, many cards in a short time, known bad cards). The money is captured but can be charged back. Agent's recommendation: keep granting at these prices, and later record the flag for the Master Admin. Mobile wallets carry far less chargeback risk than cards. Shahriar decides.

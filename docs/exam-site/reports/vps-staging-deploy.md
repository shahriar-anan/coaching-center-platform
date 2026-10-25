# VPS staging deploy — report

Running record for [../plans/vps-staging-deploy.md](../plans/vps-staging-deploy.md). One entry per slice. No secret values.

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

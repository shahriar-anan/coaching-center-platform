# VPS staging deploy — S1 to S6

Written 25 October 2026 from the code on branch `catalog-acquisition` and [../reports/new-vps-provision-audit.md](../reports/new-vps-provision-audit.md). Goals come from [../todays-goals.md](../todays-goals.md) section 3. [../decisions.md](../decisions.md) still wins where this file is silent.

**Audience:** the agent helping Shahriar. **Outcome:** by the night of 26 October the current API and website run on the new VPS at `https://srv2089893.hstgr.cloud` on the `staging` profile. One real verification mail and one reset mail have arrived. One sandbox payment has been granted through an IPN. `symamlaw.com` stays on the old VPS.

## The box today

From the 24 October audit. Re-check in slice 1 before trusting it.

| | |
|---|---|
| SSH | alias `exam-kvm` (root), or `exam-kvm` with `User=deploy` (passwordless sudo) |
| OS | Ubuntu 24.04.5, 2 vCPU, 7.8 GiB RAM, 96 GiB disk, 2 GiB swap |
| API | `/opt/coaching-api/api.jar`, `coaching-api.service`, user `deploy`, `EnvironmentFile=/etc/coaching-api.env` (`640 root:deploy`), `Restart=on-failure`, listens on `127.0.0.1:8080` |
| Jar | old build. Reads `EMAIL_*`, so registration returns 500. Profile `prod`. |
| DB | PostgreSQL 16, DB and role `coaching`, localhost only. Flyway at `33`. One Master Admin, no exams, no orders. |
| Files | `/var/lib/coaching-api/files`, `deploy:deploy`, empty |
| Nginx | `/etc/nginx/sites-available/coaching`. `/api/` goes to `127.0.0.1:8080`, `client_max_body_size 90m`, HTTP goes to HTTPS. `/` returns the text `coaching-api host ready`. |
| TLS | Let's Encrypt for `srv2089893.hstgr.cloud` only, `certbot.timer` on, expires 21 January 2027 |
| Other | UFW allows 22, 80 and 443. fail2ban guards `sshd`. Unattended upgrades on. A reboot is pending. No backup job. Node is not installed. |

## Hard stops

- **Test scope (V1):** `BookOrderIT` and `CoursePurchaseIT` cover course and book payments, which are out of this delivery ([../README.md](../README.md), "Leave these alone"). They do not block the deploy. `BootstrapIT` covers the Master Admin bootstrap, which runs on every API start on the box. It blocks.
- No DNS change, no `symamlaw.com` certificate, no Nginx `server_name` for `symamlaw.com`. That is section 5 of `todays-goals.md`, later.
- The `staging` profile only. No live SSLCommerz store, and never `securepay.sslcommerz.com`. The code refuses a live host on `staging`.
- Never print, `cat`, `grep` or echo a value from `/etc/coaching-api.env`. Check names only (slice 3). Do not read `/root/master-admin-bootstrap.txt`. Do not paste a secret into chat, Git, or a markdown file.
- Shahriar writes the secret values. The agent does not ask for them in chat.
- One API process. Never start a second jar beside the service, because the schedulers would send the same mail twice.
- Build on the PC. Do not run `mvn` or `npm run build` on the box.
- Do not touch the old VPS (`client-vps`, `88.222.215.187`).
- Before every server write (copy, file edit, package install, `systemctl restart`, `nginx -s reload`, reboot), show the command and wait for Shahriar's OK. Reads need no OK.
- Do not reopen [auth-and-mail.md](auth-and-mail.md) section 5. That Brevo and DNS work is done.

## Decisions to confirm before slice 0

Shahriar confirmed V1–V6 on 25 October 2026. V7 is picked on the day.

| # | Question | Proposed default |
|---|---|---|
| V1 | The three failing classes in the last saved test run (`BookOrderIT`, `CoursePurchaseIT`, `BootstrapIT`, 03:01 on 25 October) | Confirmed. Run the full suite. The saved failures look like leftover rows in the shared test database (409 on create, a foreign key in `BootstrapIT`'s cleanup), so a clean run may pass. If `BookOrderIT` or `CoursePurchaseIT` still fail, log them in the report and deploy anyway: course and book payments are out of this delivery. Every other class, `BootstrapIT` included, must pass. |
| V2 | About 200 files are uncommitted on `catalog-acquisition` | Commit them on the branch before the build, so the jar matches a commit. Push only if Shahriar asks. |
| V3 | How Next.js runs on the box | `output: "standalone"` in `web/next.config.ts`. Build on the PC, copy `.next/standalone`, `.next/static` and `public`, and run it with `node server.js` under a systemd unit `coaching-web.service` as user `deploy`. Use systemd, not PM2: the API already runs that way and nothing extra needs installing. |
| V4 | Node on the box | Node 22 LTS from the NodeSource apt repo. Ubuntu's own `nodejs` is too old for Next 16. |
| V5 | Nginx body limit is `90m`. Spring accepts 100 MB. | Raise it to `105m`, so an upload between 90 and 100 MB gets the API's JSON error rather than Nginx's HTML 413. |
| V6 | Where the nightly backup goes off the server | Confirmed: Backblaze B2 through `rclone`, with an `rclone crypt` remote so files are encrypted before they leave the box. Private bucket, application key limited to that bucket. Shahriar creates the account, bucket and key, and keeps the crypt password in the password manager: without it the backups cannot be read. He types the key and password into `rclone config` on the box himself, the same way as the env file. Until then the dump stays on the box, and slice 9 is not done. |
| V7 | Reboot window | After the payment check passes, at a time Shahriar picks. The reboot also proves both services start on their own. |

## Slice order

Each slice ends with its exit check passing and a line in [../reports/vps-staging-deploy.md](../reports/vps-staging-deploy.md) saying what was done, when, and the evidence. Create that report in slice 1. Do not start the next slice before that.

| # | Slice | Goal | Where |
|---|---|---|---|
| 0 | Local gate | S1 | PC |
| 1 | Server check, read only | — | Box |
| 2 | Backup before change | S6 (first half) | Box and PC |
| 3 | Env file, written by Shahriar | S2 | Box |
| 4 | Deploy the jar | S1 | Box |
| 5 | Real mail check | S3 | Box and inbox |
| 6 | Node, website, Nginx | S4 | PC and box |
| 7 | Staging seed | S2 | Box |
| 8 | Sandbox payment | S5 | Browser and box |
| 9 | Nightly off-server backup, reboot | S6 | Box |
| 10 | Close out | — | Repo |

### 0. Local gate

1. From `api/`, run `mvn test` against local PostgreSQL with `TEST_DB_URL`, `TEST_DB_USER` and `TEST_DB_PASSWORD` set. This PC has no Docker. Every class passes except, at most, `BookOrderIT` and `CoursePurchaseIT` (V1).
2. Commit (V2).
3. `mvn -DskipTests package`. The suite just passed on the same commit. Record the SHA-256 of `api/target/api-0.0.1-SNAPSHOT.jar` and the commit hash.
4. Web: apply V3 (one line in `next.config.ts`). Read `web/node_modules/next/dist/docs/` on standalone output first, as `web/AGENTS.md` requires. Build with `NEXT_PUBLIC_API_BASE_URL=https://srv2089893.hstgr.cloud`. The value is baked in at build time and is not secret, so set it in the shell for this build and do not change `web/.env.local`. Run `npm run lint` and `npm test` first.
5. Check the standalone folder for native modules built for Windows (for example `sharp`, or anything under `node_modules/@img`). A Windows binary will not load on Linux. The build bundles `@img/sharp-win32-x64`. Shahriar approved this swap on 25 October 2026: in an empty scratch folder, `npm install --os=linux --cpu=x64 --libc=glibc sharp@<version in standalone/node_modules/sharp>`, copy its `@img/sharp-linux-x64` and `@img/sharp-libvips-linux-x64` into `.next/standalone/node_modules/@img/`, and delete `@img/sharp-win32-x64`. Do not run `npm install` inside the standalone folder: it would install every dependency in its `package.json`. Repeat after every web build.

**Exit:** suite green apart from the V1 exception, commit hash and jar SHA recorded, `web/.next/standalone/server.js` exists, and `find .next/standalone -name '*.node'` lists only Linux files.

### 1. Server check, read only

Over `exam-kvm`. Compare with the table above and record each difference.

- `systemctl status coaching-api nginx --no-pager`, `sha256sum /opt/coaching-api/api.jar`, `df -h /`, `free -h`, `ls /var/run/reboot-required`.
- Env **names** only: `sudo sed -E 's/=.*//' /etc/coaching-api.env | sort`.
- Flyway: `sudo -u postgres psql -d coaching -Atc "select max(installed_rank), bool_and(success) from flyway_schema_history"`.
- `sudo nginx -T` for the `coaching` site only.
- `command -v node`. Expected: none.

**Exit:** the report exists with the observed state.

### 2. Backup before change

The new jar applies Flyway `V34` to `V36` on start. Flyway only moves forward.

1. `sudo -u postgres pg_dump -Fc coaching > /var/backups/coaching-pre-v36-<date>.dump` (mode `600`, owner root).
2. `sudo cp /opt/coaching-api/api.jar /opt/coaching-api/api.jar.prev`
3. `sudo cp /etc/coaching-api.env /etc/coaching-api.env.prev` (keep the mode and owner: `cp -p`).
4. Copy the dump to the PC, outside the repo (for example `F:\exam-vps-backups\`). Compare SHA-256 on both ends.

**Exit:** the dump, the old jar and the old env file exist on the box. The dump's checksum matches on the PC.

### 3. Env file, written by Shahriar

The agent shows this table. Shahriar edits `/etc/coaching-api.env` on the box (`sudo nano /etc/coaching-api.env`). The agent then checks names with the slice 1 command, and blank values with `sudo awk -F= '$2==""{print $1}' /etc/coaching-api.env`, which prints names only.

| Name | Value | Note |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` | Was `prod`. Staging accepts only the sandbox host and allows the seed. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | keep | |
| `JWT_SECRET` | keep | Changing it signs out the Master Admin. Harmless, but not needed. |
| `WEB_ORIGIN` | `https://srv2089893.hstgr.cloud` | Was `https://symamlaw.com`. Required on staging. |
| `FILE_STORAGE_DIR` | keep `/var/lib/coaching-api/files` | |
| `SERVER_ADDRESS`, `SERVER_PORT` | keep | |
| `MASTER_ADMIN_PHONE`, `_EMAIL`, `_NAME` | keep | Only read when no Master Admin exists. |
| `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_FROM` | **delete** | The new jar does not read them. |
| `MAIL_HOST` | `smtp-relay.brevo.com` | Staging refuses to start if blank. |
| `MAIL_PORT` | `587` | |
| `MAIL_USERNAME` | Brevo SMTP login | From the password manager. Not the API key. |
| `MAIL_PASSWORD` | Brevo SMTP key | From the password manager. |
| `MAIL_FROM_ADDRESS` | `no-reply@symamlaw.com` | |
| `MAIL_FROM_NAME` | `SYMAM LAW` | |
| `MAIL_REPLY_TO` | leave out | |
| `MAIL_TEST_ENABLED` | `false` | `true` only during slice 5 if the admin test mail is used, then back to `false`. |
| `SSLCZ_STORE_ID`, `SSLCZ_STORE_PASSWORD` | sandbox pair | The pair that returns store name Demo. Never the live pair. |
| `SSLCZ_BASE_URL` | `https://sandbox.sslcommerz.com` | |
| `SSLCZ_CALLBACK_BASE_URL` | `https://srv2089893.hstgr.cloud` | No path, no trailing slash. |
| `SEED_ENABLED` | `false` | `true` only for slice 7. |
| `SEED_ADMIN_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Shahriar's choice | Phones `01XXXXXXXXX`, all different and not `MASTER_ADMIN_PHONE`. Passwords 8–72 characters. |
| `SEED_STUDENT_PHONE`, `_EMAIL`, `_NAME`, `_PASSWORD` | Shahriar's choice | Use an inbox Shahriar can open, so slice 5 can reuse it if wanted. |
| `GOOGLE_*` | leave out | Google sign-in waits (`todays-goals.md` section 2). |

File syntax is systemd `EnvironmentFile`: one `NAME=value` per line, no `export`, no spaces around `=`. If a secret contains `"`, `'`, `\` or `$`, wrap the whole value in single quotes. Keep `640 root:deploy`.

**Exit:** every name above is present or deliberately left out, no required name is blank, and the `EMAIL_*` names are gone.

### 4. Deploy the jar

1. `scp` the jar to `/tmp/api.jar`, check its SHA-256 against slice 0, then `sudo install -o root -g root -m 644 /tmp/api.jar /opt/coaching-api/api.jar`.
2. `sudo systemctl restart coaching-api`, then `journalctl -u coaching-api -f` until the app has started or exited.
3. Expected in the log: profile `staging`, Flyway migrated to `36`, `Started ApiApplication`. No secret on any line.
4. From the PC: `GET https://srv2089893.hstgr.cloud/api/v1/public/live-exam-categories` returns 200. `GET /api/v1/me` with no token returns 401 `UNAUTHENTICATED`.
5. Flyway query from slice 1 returns `36|t`.

**If it fails to start:** read the first `IllegalStateException` line. Usually it names a missing env value: fix the env file and restart. If the jar itself is bad, put `api.jar.prev` and `coaching-api.env.prev` back. If the old jar then refuses the `V34`–`V36` schema, restore the slice 2 dump (`pg_restore --clean -d coaching`) and get Shahriar's OK first.

**Exit:** the service is active on the new jar, Flyway is at 36, and both public checks pass.

### 5. Real mail check

Follows [auth-and-mail.md](auth-and-mail.md) section 4, steps 3–6.

1. Brevo's IP lock stays off, or `187.52.115.111` is on its allow list (Shahriar confirms).
2. Register one student with an email Shahriar can open: `POST /api/v1/auth/register` with `curl` from the PC. The website is not up yet. Then `POST /api/v1/auth/forgot-password` for an `ACTIVE` account. A pending account does not get a reset, so verify first.
3. Shahriar confirms both messages arrived, from `no-reply@symamlaw.com`, display name `SYMAM LAW`, and not in spam.
4. Look in `journalctl -u coaching-api` for the send lines. No SMTP key, no code, and no full address where a masked one is enough.
5. If `MAIL_TEST_ENABLED` was set to `true`, set it back to `false` and restart.

**Exit:** both mails received. The log is clean. `MAIL_TEST_ENABLED=false`.

### 6. Node, website, Nginx

1. Install Node 22 LTS (V4). Check with `node -v`.
2. `sudo install -d -o deploy -g deploy /opt/coaching-web`. Copy `web/.next/standalone/` there, then `web/.next/static` to `/opt/coaching-web/.next/static` and `web/public` to `/opt/coaching-web/public`.
3. Add `/etc/systemd/system/coaching-web.service`: `User=deploy`, `WorkingDirectory=/opt/coaching-web`, `ExecStart=/usr/bin/node server.js`, `Environment=NODE_ENV=production HOSTNAME=127.0.0.1 PORT=3000`, `Restart=on-failure`. Then `daemon-reload`, `enable --now`.
4. Nginx `coaching` site: change `location /` from the plain-text reply to `proxy_pass http://127.0.0.1:3000` with `Host`, `X-Forwarded-For` and `X-Forwarded-Proto` headers. Leave `location /api/` as it is. Set `client_max_body_size 105m` (V5). Run `sudo nginx -t`, then `sudo systemctl reload nginx`.
5. Port 3000 must not be public. UFW already allows only 22, 80 and 443. Check that `ss -ltn` shows `127.0.0.1:3000`.
6. From the PC, `/`, `/login`, `/register`, `/live-exams` and `/purchase/result` each return 200. The page code calls `https://srv2089893.hstgr.cloud/api/...`, not `localhost`. Check in the browser's network tab, or grep the built JS on the PC.

**Exit:** both services enabled and active, Nginx config valid, pages served over HTTPS, and no request goes to `localhost`.

**Redeploying the website later:** build on the PC, swap the `sharp` binary (slice 0 step 5), copy into a new folder, swap folders, then `systemctl restart coaching-web`.

### 7. Staging seed

1. Set `SEED_ENABLED=true` and restart the API. The log shows one system admin, one student, and the published 10.00 BDT exam "Staging practice exam".
2. Set `SEED_ENABLED=false` and restart again.
3. `GET /api/v1/public/live-exam-categories` now lists the seed category.

**Exit:** the seed rows exist once and `SEED_ENABLED=false`. A second seeded start does not make duplicates.

### 8. Sandbox payment

Follows [../reports/sslcommerz-sandbox-runbook.md](../reports/sslcommerz-sandbox-runbook.md).

1. Shahriar signs in as the seed student at `https://srv2089893.hstgr.cloud/sandbox`, buys the practice exam, and pays with a sandbox test card. Never a real card.
2. The browser lands on `/purchase/result?tranId=…` and shows paid.
3. The log shows, for that `tranId`: `sslcommerz session accepted`, `sslcommerz validation accepted`, `live exam payment granted`, and `request completed method=POST path=/api/v1/live-exam-orders/<tranId>/ipn status=200`. **The IPN line is the S5 proof.** A grant from the browser return alone does not prove the IPN.
4. DB: the order is `SUCCEEDED` with one grant row for that student and exam.
5. The receipt mail arrives at the student inbox.
6. From the runbook's unhappy-path list, run: fail, cancel, and the startup guard (`staging` with the live host must exit). Do not leave a guard-test process running, and put the env back afterwards.

**If no IPN arrives:** check that the sandbox merchant panel has no fixed IPN URL overriding the per-payment URL. Check that `access.log` in Nginx shows the POST. The 10-minute recheck job may still grant the order, but S5 is not done until an IPN lands.

**Exit:** one granted order with an IPN 200, the receipt mail received, and the three unhappy checks pass.

### 9. Nightly off-server backup, reboot

1. Install `rclone` from the Ubuntu archive. Shahriar runs `sudo rclone config` and creates a `b2` remote, then a `crypt` remote over `b2:<bucket>/coaching`. The config lives at `/root/.config/rclone/rclone.conf`, mode `600`. The agent never opens it.
2. Script `/usr/local/sbin/coaching-backup` (root, `700`): `pg_dump -Fc coaching` plus a tar of `/var/lib/coaching-api/files`, both under `/var/backups/coaching/` with a date in the name. Keep 14 days on the box. `rclone copy` the new files to the crypt remote. On B2, a bucket lifecycle rule keeps 30 days.
3. A systemd timer `coaching-backup.timer` at 03:00 Asia/Dhaka, `Persistent=true`.
4. Run it once by hand. Check that `rclone ls` on the crypt remote lists the new files. Restore that dump into a scratch database (`createdb coaching_restore_check`, `pg_restore`, count the `users` rows, `dropdb`). An untested backup does not count.
5. Reboot (V7). Afterwards `coaching-api`, `coaching-web`, `nginx`, `postgresql` and `coaching-backup.timer` are active without anyone starting them, `/var/run/reboot-required` is gone, and the slice 4 and 6 public checks still pass.

**Exit:** the timer is listed, one off-server copy exists, the restore check passed, and everything came back after the reboot.

### 10. Close out

- Finish [../reports/vps-staging-deploy.md](../reports/vps-staging-deploy.md): commit, jar SHA, what changed on the box (files, units, Nginx diff, packages), and each slice's evidence. No secret values.
- Mark S1–S6 done in [../todays-goals.md](../todays-goals.md), each with a link to the report.
- List what is left for section 5 of `todays-goals.md` (prod profile, `MAIL_TEST_ENABLED` removal, live or sandbox store, DNS, certificate, origins).

## Rollback summary

| Layer | Undo |
|---|---|
| Jar | `api.jar.prev` plus `coaching-api.env.prev`, then restart. Restore the slice 2 dump if the old jar refuses the schema. |
| Website | `systemctl disable --now coaching-web`, then put back the old `location /` block in Nginx and reload. |
| Nginx | Keep a copy of `sites-available/coaching` before editing it. Copy it back, run `nginx -t`, reload. |
| Seed data | Staging only. Leave it until the client test is done. |

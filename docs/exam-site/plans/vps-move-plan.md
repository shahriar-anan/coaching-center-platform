# Plan: move production to a new VPS

Written 25 Nov 2026. **Overtaken on 26 Nov 2026:** the old box went down before the cutover, and production was restored on the new box from backup. Record: `../reports/client-review-deploy.md`, "Emergency move to the new VPS".

## 1. What runs on the current box today

Read-only inventory of `exam-kvm` (`187.52.115.111`, `srv2089893.hstgr.cloud`) on 25 Nov 2026:

| Layer | What is there | How it moves |
|---|---|---|
| OS | Ubuntu 24.04.5, 2 vCPU, 8 GiB, 96 GB disk (17 GB used), 2 GiB swap, time zone UTC. Reboot pending. | Same OS and size on the new box. Keep UTC. |
| Database | PostgreSQL 16.15, database `coaching` (28 MB), role `coaching`, localhost only, Flyway **107**, 0 failed. 272 users, 9 orders. | `pg_dump -Fc` and `pg_restore` at cutover. |
| Uploaded files | `/var/lib/coaching-api/files`, 650 MB, 285 files, owner `deploy`. Includes PDF view copies. | `rsync` early, final delta at cutover. |
| API | `/opt/coaching-api/api.jar` (Java 21.0.13), `coaching-api.service`, user `deploy`, `127.0.0.1:8080`. | **Copy the running jar.** Do not rebuild. |
| Website | `/opt/coaching-web` (87 MB, Next.js standalone, Linux `sharp`), Node 22.23.4, `coaching-web.service` with `SITE_URL` and `STAFF_URL`, `127.0.0.1:3000`. | **Copy the running folder.** Do not rebuild. |
| Secrets | `/etc/coaching-api.env`: DB, `JWT_SECRET`, Google, Brevo SMTP, **live SSLCommerz store**, **`PAYOUT_ENCRYPTION_KEY` (v1)**, offline flags. | Copied box to box, byte for byte. Never through the repo or chat. |
| Nginx | Sites `symamlaw.com` (+ www 308), `admin.symamlaw.com`, `exam.symamlaw.com` (308), `admin.exam.symamlaw.com` (308), plus old staging names `coaching` (hstgr) and `coaching-admin` (nip.io). `timed` log format in `nginx.conf`. Body limit 105m. | Copy the four symamlaw sites and the log format. Drop the two staging names. |
| Certificates | Let's Encrypt, certbot timer, HTTP-01. Certs for the four symamlaw names valid to 31 Jan to 2 Feb 2027. | Copy `/etc/letsencrypt` (see 4.3). |
| Backups | `/etc/cron.d/coaching-backup` 02:30 Dhaka runs `/usr/local/bin/coaching-backup`: DB dump + files tar, 14 days, **on the same disk only** (4.7 GB). Plus 2.6 GB of release backups. | Copy script and cron. Add an off-server copy (section 7). |
| Security | UFW 22/80/443, fail2ban `sshd`, root key-only, `deploy` with sudo, unattended upgrades, sysstat. | Same setup on the new box. |
| DNS (Namecheap) | A records: `@` and `www` (TTL 300); `admin`, `exam`, `admin.exam` (TTL 1799). No AAAA. MX = Namecheap mail forwarding; SPF, Brevo code, DKIM, DMARC TXT. | Change the five A records only. Mail records stay. |

**Outside services** are tied to the domain, not the server address, so they keep working: SSLCommerz callback base `https://symamlaw.com`, Google callback `https://symamlaw.com/api/v1/auth/google/callback`, Brevo SMTP. Two things to check in their panels (section 3).

## 2. The approach

Build the new box as an **exact copy** of the running system. Move the binaries that are already live (jar, website folder), not a new build. No feature work ships with the move. The course work on `lms/integration` stays out until the move is finished.

Order: build, then rehearse with a copy of the data in a **safe mode** (no mail, no payments, no jobs). Then do a short cutover at night: stop writes on the old box, copy the final data, start the new box, and switch DNS. The old box then forwards any late traffic to the new box, so nothing is written in two places.

Expected break for users: **about 10 to 15 minutes**. The database is 28 MB, and the files are synced in advance.

## 3. Needed from Shahriar before Stage 1

1. **Expiry date** of the current VPS: **23 December 2026** (hPanel, seen 25 Nov). Auto-renewal at $24.49 failed for insufficient balance. Aim to cut over by about 16 December, so a week is left for rollback.
2. **The new VPS (decided 25 Nov):** Hostinger KVM 2, 24-month plan, in the **client's own** Hostinger account, bought 25 Nov. The old account's auto-renew stays off. Setup: plain OS **Ubuntu 24.04** (no panel or app template), the closest data center to Bangladesh, the SSH key `symam-vps-2026` (`C:\Users\HP\.ssh\symam_vps_ed25519.pub`) added at setup, auto-renew on, and the free weekly backups on. Shahriar gets access through hPanel account sharing, not the client's password. Needed back: the IPv4 address and confirmation that the key works.
3. **Namecheap login** at cutover time to change the five A records (you have done this before).
4. **Panels to check** (just look, no change):
   - SSLCommerz merchant panel: is any **server IP** registered or whitelisted? If yes, the new IP has to be added before cutover.
   - Brevo: Security, **Authorised IPs**. If it is on, the new IP has to be added. (This might also explain why Brevo mail is not delivering today.)
5. **Window:** a night with no exam open and none within about 3 hours, for example 02:00 to 04:00 Dhaka.
6. **No releases** from now until the move is done. Or tell me which release goes first.

Quicker option to check: if the new box is with the same provider, ask whether the current VPS can simply be **renewed or moved to another account**. That avoids the move.

## 4. Stages

Each stage stops at a gate for Shahriar's go, as with past releases. Box steps run through scratchpad scripts (`cmd /c "ssh <alias> bash -s < script.sh"`). Long steps run detached with `nohup`, because the PC's SSH link drops connections.

### Stage 1 — Build the new box (any day, no risk to live)

1. New SSH alias `exam-kvm2` in `C:\Users\HP\.ssh\config` (new key). Keep `exam-kvm` for the old box.
2. Base: `apt full-upgrade`, reboot, hostname, UTC, 2 GiB swap, user `deploy` (key, sudo), sshd key-only, UFW 22/80/443, fail2ban, unattended upgrades, sysstat.
3. Stack, **same versions**: `openjdk-21-jre-headless`, PostgreSQL 16 (localhost only, peer + scram as now), Node **22.x** (the same major version as now), nginx, certbot + `python3-certbot-nginx`.
4. Database role `coaching` and an empty database `coaching`. The password comes from the copied env file, set by Shahriar or by a script that reads it without printing it.
5. Folders: `/opt/coaching-api`, `/opt/coaching-web`, `/var/lib/coaching-api/files` (`deploy:deploy`, 750), `/var/backups/coaching`.
6. Copy **box to box** (a temporary key on the new box, removed afterwards), and compare SHA-256 on both sides:
   - `api.jar` (the live one, from the 25 Nov "pay" release);
   - `/opt/coaching-web` as a tar;
   - `/etc/coaching-api.env` (mode 640, `root:deploy`);
   - the two unit files, the four nginx site files, the `log_format timed` line, `/usr/local/bin/coaching-backup` and its cron file;
   - `/etc/letsencrypt` as a tar that keeps symlinks.
7. Nginx: enable only the four symamlaw sites, plus a default server that answers 444 for unknown hosts. `nginx -t`.

**Gate 1:** both services *installed* but **stopped**. The live env file is in place but not used yet.

### Stage 2 — Rehearsal with a copy of live data (safe mode)

1. On the old box: dump the DB and tar the files (read-only), send them box to box, restore on the new box.
2. Start the API with a **rehearsal env**: a copy of the env with every `MAIL_*` and `SSLCZ_*` line removed and a throwaway `JWT_SECRET`. Keep the real `PAYOUT_ENCRYPTION_KEY`, so we can prove that the payout numbers decrypt. Add `--app.exam-jobs.scheduled=false` and `STAFF_PAY_JOBS_ENABLED=false`.
   **Why:** with a copy of live data, the reminder and deadline jobs would otherwise email real students or settle sittings twice.
3. Checks from the PC with `curl --resolve symamlaw.com:443:<new IP>`, and in a browser through a `hosts` file entry (only on this PC):
   - Flyway 107, no migration applied, 0 errors;
   - row counts the same as the source dump (users, sittings, orders, grants, notices, admissions, classes);
   - home, `/admission` (the offline flag is compiled in), `/packages`, the admin login, the PDF viewer `/pdfjs/` assets;
   - a sign-in as Master Admin, and a payout account screen that shows the number (this proves the key);
   - one uploaded PDF and one avatar open;
   - the preflight with `BOX_IP=<new IP>`.
4. Stop the API, wipe the rehearsal DB and files (they become stale), and put the real env back in place.

**Gate 2:** the rehearsal report is accepted by Shahriar and the cutover window is picked.

### Stage 3 — Day before cutover

1. Namecheap: lower the TTL of `admin`, `exam` and `admin.exam` from 1799 to 300. `@` and `www` are already 300.
2. First full `rsync` of the files from old to new, so that cutover only copies the changes.
3. Check the old box: `certbot renew --dry-run` and disk space. Make sure no release is planned.

### Stage 4 — Cutover (about 30 minutes, user-facing break about 10 to 15)

1. **Pre-check on the old box:** no open sitting, no exam opening within 3 hours, no payment started in the last 15 minutes. Same queries as earlier releases. If any check fails, wait.
2. **Stop writes on the old box:** nginx serves a short maintenance page (503 with `Retry-After`) for both hosts, then `systemctl stop coaching-web coaching-api`.
3. **Final copy:** `pg_dump -Fc`, plus `pg_restore --list` to check it. Final `rsync --delete` of the files. Send both box to box and check SHA-256. Keep a copy of the dump on the PC (`F:\exam-vps-backups\`).
4. **New box:** restore the DB, start `coaching-api` with the **real env** (expect Flyway 107, none applied, start in about 25 s), start `coaching-web`, reload nginx.
5. **Check the new box before DNS** (with `--resolve`): row counts equal the final dump; the live preflight `MODE=live BOX_IP=<new IP>`; admin sign-in.
6. **Old box forwards to new:** swap the old nginx sites for a forwarder (`proxy_pass https://<new IP>` with `proxy_ssl_server_name on` and the original `Host`). Anyone whose DNS still points at the old address (including an SSLCommerz IPN) lands on the new box. Nothing is ever written to the old database again.
7. **DNS:** change the five A records to the new IP. Check from the new box with `dig @1.1.1.1` and `@dns1.registrar-servers.com` (not from the PC; its DNS is intercepted).

**Rollback** at any step before 7: start the services on the old box again and remove the maintenance page. The old box is untouched. After 7, put the A records back and reverse the forwarder. This only works while the old box is alive, and only if no new writes on the new box must be kept (otherwise dump the new box and restore it on the old one first).

### Stage 5 — After cutover (same night and next day)

1. The preflight from the PC through public DNS once it resolves; both hosts 200; `/admission` renders; admin stays signed in after a reload.
2. **Real payment check:** the pending 10 BDT live test from 5 Nov (pay, IPN reaches the **new** box in `journalctl -u coaching-api`, receipt, refund from the merchant panel).
3. A Google sign-in, a password reset mail (Brevo), one PDF open on a phone.
4. `certbot renew --dry-run` on the new box. Watch for the first real renewal around early January.
5. The next night: the 02:30 backup ran and its dump restores (`pg_restore --list`). The jobs ran (06:30 instalment reminder, deadlines) with no errors.
6. Watch the old box's nginx log for forwarded requests. When they stop (24 to 48 h), the forwarder is no longer needed.

### Stage 6 — Tidy up

1. Repo: `live-payment-preflight.sh` default `BOX_IP`, `deploy-web.ps1` default `$Box`, the PC SSH alias (`exam-kvm` points to the new box), `PROVISION.txt` on the new box, a record in `reports/client-review-deploy.md`, and memory notes.
2. `deploy-on-box.sh` copies the Linux `sharp` from the live release. That keeps working because we copied the live folder. Note this for any future fresh box.
3. **Old box, before it expires and only on Shahriar's go:** a last copy of `/var/backups/coaching` that we want to keep on the PC, then delete the live secrets (`/etc/coaching-api.env*`, which hold the live store, payout key and JWT secret), the dumps and the files tars (student data). Hostinger may keep disk images after expiry.
4. Optional cleanup on the new box: none of the 27 old jar copies and 31 old website copies move, only the last two of each.

## 5. Risks and how the plan handles them

| Risk | Handling |
|---|---|
| Payout numbers unreadable on the new box | The same `PAYOUT_ENCRYPTION_KEY` and version are copied. The rehearsal proves decryption on a payout row. |
| A rebuild changes behaviour (offline flag, API base, `sharp`) | No rebuild: the running jar and website folder are copied as they are. |
| Real mail or a double settlement from the rehearsal copy | Safe-mode env: no mail, no SSLCommerz, jobs off. Rehearsal data is wiped before cutover. |
| A payment in flight at cutover | The pre-check waits until none has started in 15 minutes. The old box forwards a late IPN to the new box. |
| Data written on both boxes | The old API is stopped before the final dump. The old box only forwards after that. |
| Sign-ins lost | The same `JWT_SECRET` and the same host names: sessions and refresh cookies keep working. |
| Certificate chicken-and-egg (HTTP-01 needs DNS first) | The existing certs are copied. They are valid to February, and renewal works once DNS points to the new box. |
| SSLCommerz or Brevo tied to the old IP | Checked in their panels before cutover (section 3, item 4). |
| Backups die with a box | Today all backups sit on the box that is expiring. A copy to the PC at cutover, and an off-server job afterwards (section 7). |
| Old box expires during the move | Start Stage 1 at least 3 days before expiry. The cutover is at least 1 day before. |

## 6. Timeline (needs the expiry date)

| When | Stage |
|---|---|
| Day 1 | Answers to section 3, buy the new box, Stage 1 |
| Day 1–2 | Stage 2 rehearsal, Gate 2 |
| Day before cutover | Stage 3 (TTL, first rsync) |
| Cutover night, 02:00–04:00 Dhaka | Stage 4 |
| Next 2 days | Stage 5 checks, old box forwards |
| Before expiry | Stage 6 old-box cleanup |

## 7. Recommended alongside (not a blocker)

**Off-server backups.** Today the nightly dumps are on the same disk as the database. Two simple options:
(a) a nightly pull to the PC (works only when the PC is on), or
(b) push to object storage, such as Backblaze B2 or Hostinger object storage, with a restore tested once. My recommendation is (b), set up during Stage 5.

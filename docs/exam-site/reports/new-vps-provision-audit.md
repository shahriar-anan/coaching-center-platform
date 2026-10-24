# New exam VPS provision audit

| | |
|---|---|
| Date | 24 October 2026, about 07:40–08:05 UTC |
| SSH alias | `exam-kvm` (root) and `exam-kvm` with `User=deploy` |
| Hostname | `srv2089893` / `srv2089893.hstgr.cloud` |
| Public IPv4 | `187.52.115.111` |
| Public IPv6 | `2a02:4780:5e:d375::1` |
| Mode | Read-only. No file, package, service, Nginx, firewall, DB, certificate, or DNS change. No user, exam, or order created. No register request sent. |

## Summary

The box matches the provision claims. The API is running the same jar as the local build (`api/target/api-0.0.1-SNAPSHOT.jar`, SHA-256 `59ae12c6…805a30`). The schema is at Flyway 33. The public read works over HTTPS. Student registration fails with HTTP 500 because `EMAIL_HOST=127.0.0.1:25` has no listener. SSLCommerz and Google are not configured. No `symamlaw.com` name points at this box yet.

Two things were not claimed but were observed. The kernel has `/var/run/reboot-required` set. No off-server backup job exists.

## Source conflict

At audit time, `docs/exam-site/decisions.md` still said the KVM 2 was not purchased, and `docs/exam-site/client-brief.md` section 10 still said the client would buy it. Those files were not edited during the audit. They were updated afterward. `decisions.md` now records the purchased box and the Gmail SMTP decision.

## Claim table

| Claim | Result | Evidence |
|---|---|---|
| OS: Ubuntu 24.04.5 LTS | **Verified** | `/etc/os-release`: `Ubuntu 24.04.5 LTS`. Kernel `6.8.0-142-generic`. |
| Size: 2 vCPU, ~8 GiB RAM, ~96 GiB disk, 2 GiB swap at `/swapfile` | **Verified** | `nproc` 2 (AMD EPYC 9354P). `free -h` 7.8 GiB. `df -h /` 96 G, 3.8 G used. `swapon --show` `/swapfile` 2 G, in `/etc/fstab`. |
| SSH: root key login, password login off, `deploy` can sudo | **Verified** | `ssh -o BatchMode=yes exam-kvm true` exit 0. `ssh -o BatchMode=yes -o User=deploy exam-kvm sudo -n true` exit 0. `sshd -T`: `passwordauthentication no`, `kbdinteractiveauthentication no`, `permitrootlogin without-password`, `pubkeyauthentication yes`. `/etc/sudoers.d/deploy` exists. `/home/deploy/.ssh/authorized_keys` is `600 deploy:deploy`. |
| Firewall: UFW active, incoming deny, allow 22/80/443 | **Verified** | `ufw status verbose`: active, `deny (incoming)`, `allow (outgoing)`. Rules: OpenSSH 22/tcp, Nginx Full 80,443/tcp, IPv4 and IPv6. |
| fail2ban active | **Verified** | `systemctl is-active fail2ban`: active. `fail2ban-client status`: 1 jail, `sshd`. |
| `unattended-upgrades` installed | **Verified** | `dpkg -l`: `2.9.1+nmu4ubuntu1`, enabled and active. `20auto-upgrades` sets both periodic values to `1`. |
| PostgreSQL 16, DB `coaching`, role `coaching`, localhost only | **Verified** | `select version()`: 16.15. `show listen_addresses`: `localhost`. Sockets `127.0.0.1:5432` and `[::1]:5432`. DB `coaching` owned by role `coaching` (login, not superuser). `pg_hba`: peer locally, `scram-sha-256` from 127.0.0.1 and ::1 only. Role password not read. |
| Java: OpenJDK 21 JRE, `java -jar /opt/coaching-api/api.jar` | **Verified** | `java -version`: 21.0.12.1. Package `openjdk-21-jre-headless`. `ps`: `deploy … /usr/bin/java -jar /opt/coaching-api/api.jar`. |
| systemd: `coaching-api.service` enabled, user `deploy`, env file `/etc/coaching-api.env` mode `640`, group `deploy` | **Verified** | `systemctl is-enabled/is-active`: enabled, active since 2026-10-23 15:18:03 UTC, 0 restarts. `User=deploy`, `Group=deploy`, `EnvironmentFile=/etc/coaching-api.env`, `Restart=on-failure`. `stat`: `-rw-r----- root:deploy`. |
| Files: `/var/lib/coaching-api/files` owned by `deploy` | **Verified** | `stat`: `drwxr-x--- deploy:deploy`. Empty. Parent `/var/lib/coaching-api` is `root:root 755`. |
| Nginx: `/api/` → `127.0.0.1:8080`, `client_max_body_size 90m`, HTTP → HTTPS for this hostname | **Verified** | `/etc/nginx/sites-available/coaching` (only enabled site). `nginx -t` OK. `curl http://srv2089893.hstgr.cloud/api/v1/public/live-exam-categories` → 301 to the HTTPS URL. |
| Certificate: Let's Encrypt for `srv2089893.hstgr.cloud` only, auto-renew timer, expiry 21 January 2027 | **Verified** | `certbot certificates`: one cert, ECDSA, domain `srv2089893.hstgr.cloud`, expiry 2027-01-21 14:15:51 UTC (89 days). Workstation TLS read: CN and SAN `srv2089893.hstgr.cloud`, issuer Let's Encrypt `YE1`, TLS 1.3. `certbot.timer` enabled, next run 2026-10-24 22:17 UTC. |
| Schema: Flyway history reaches `33` | **Verified** | `flyway_schema_history`: max rank 33, 33 rows, 0 failed. Latest `33 google identity`. Startup log: `Schema "public" is up to date`. |
| Public read: `GET /api/v1/public/live-exam-categories` 200 over HTTPS | **Verified** | Workstation `curl`: 200, body `[]`, certificate verified. |
| Auth: no token → 401 on `/api/v1/me`. Master Admin can log in. Password only in `/root/master-admin-bootstrap.txt` | **Verified** (login indirectly) | `/api/v1/me` → 401, `code` `UNAUTHENTICATED`. `users`: one `MASTER_ADMIN`, `ACTIVE`, `last_login_at` 2026-10-24 07:42:09 UTC, one refresh token issued at that time. I did not log in myself. `MASTER_ADMIN_PASSWORD` is absent from the env file. `/root/master-admin-bootstrap.txt` exists, `600 root:root`, 195 bytes; contents not read. |
| Mail: `EMAIL_HOST` placeholder `127.0.0.1`. Register returns 500 because nothing listens on 25 | **Verified** | Env: `EMAIL_HOST=127.0.0.1`, `EMAIL_PORT=25`. `ss -ltn`: nothing on 25. Log, 2026-10-24 07:42:08 and 07:42:19 UTC: `POST /api/v1/auth/register status=500`, `MailSendException … Couldn't connect to host, port: 127.0.0.1, 25 … Connection refused`. No student row exists, so both attempts rolled back. No new register was sent. |
| Payments: `SSLCZ_*` unset, live store not copied | **Verified** | All four `SSLCZ_*` names are absent from the env file. |
| Google: `GOOGLE_*` unset | **Verified** | All three `GOOGLE_*` names are absent. |
| Website: Next.js not installed | **Verified** | `command -v node npm pm2`: none. `/opt` has only `coaching-api`. Nginx `/` returns plain text `coaching-api host ready`. |
| DNS: `symamlaw.com` still on `88.222.215.187`; this box reached by `srv2089893.hstgr.cloud` | **Verified** | See DNS below. |

## Listeners and firewall

| Port | Process | Bound to |
|---|---|---|
| 22 | `sshd` (socket-activated) | `0.0.0.0`, `[::]` |
| 80 | `nginx` | `0.0.0.0`, `[::]` |
| 443 | `nginx` | `0.0.0.0`, `[::]` |
| 5432 | `postgres` | `127.0.0.1`, `[::1]` |
| 8080 | `java` | `127.0.0.1` only (shown as `[::ffff:127.0.0.1]`; `SERVER_ADDRESS` is set) |
| 25 | none | — |

UFW allows only 22, 80 and 443, on IPv4 and IPv6. fail2ban guards `sshd`.

## Public HTTPS checks (from the workstation)

| Request | Result |
|---|---|
| `https://srv2089893.hstgr.cloud/` | 200, `coaching-api host ready`, certificate verified |
| `https://srv2089893.hstgr.cloud/api/v1/public/live-exam-categories` | 200, `[]`, certificate verified |
| `https://srv2089893.hstgr.cloud/api/v1/me` (no token) | 401, `UNAUTHENTICATED` |
| `http://srv2089893.hstgr.cloud/api/v1/public/live-exam-categories` | 301 to HTTPS |
| `http://187.52.115.111/` | 404 (default HTTP server only redirects this hostname) |
| `https://187.52.115.111/` | TLS name mismatch, as expected |

The certificate name matches `srv2089893.hstgr.cloud`. IPv6 HTTPS was not tested from outside because the workstation has no IPv6 route (`curl -6` exit 6).

## Environment names

Only names and set or empty status were read. No secret value was printed.

| Status | Names |
|---|---|
| Set | `SPRING_PROFILES_ACTIVE` (`prod`), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `WEB_ORIGIN` (`https://symamlaw.com`), `FILE_STORAGE_DIR` (`/var/lib/coaching-api/files`), `EMAIL_HOST` (`127.0.0.1`), `EMAIL_PORT` (`25`), `EMAIL_FROM`, `MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME` |
| Absent | `EMAIL_USERNAME`, `EMAIL_PASSWORD`, `SSLCZ_STORE_ID`, `SSLCZ_STORE_PASSWORD`, `SSLCZ_BASE_URL`, `SSLCZ_CALLBACK_BASE_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `MASTER_ADMIN_PASSWORD` |
| Present, not on the list | `SERVER_ADDRESS`, `SERVER_PORT` |

None of the listed names is present with an empty value. The values printed in brackets are not secrets.

`MASTER_ADMIN_PASSWORD` being absent is safe now that a Master Admin row exists. The startup log shows three failures on 23 October at 15:16–15:17 UTC with "MASTER_ADMIN_… are required when no Master Admin exists", before the bootstrap succeeded at 15:17:47. Four earlier failures (15:15–15:16 UTC) were `UnsatisfiedDependencyException` on `authController`. Those ended before the current process started and do not affect it.

## DNS: this box versus the old VPS

Queried through `1.1.1.1` and `8.8.8.8`; both agreed.

| Name | A | AAAA |
|---|---|---|
| `symamlaw.com` | `88.222.215.187` (old VPS) | none |
| `www.symamlaw.com` | `88.222.215.187` (old VPS) | none |
| `api.symamlaw.com` | `88.222.215.187` (old VPS) | none |
| `srv2089893.hstgr.cloud` | `187.52.115.111` (this box) | `2a02:4780:5e:d375::1` |

No `symamlaw.com` name points at `187.52.115.111`. At this audit the zone's nameservers were `ns1.dns-parking.com` and `ns2.dns-parking.com`.

**Update, 24 October 2026, later.** Those nameservers are retired for this domain. Public NS are `dns1.registrar-servers.com` and `dns2.registrar-servers.com` (Namecheap SimpleDNS). Further record edits go in Namecheap Advanced DNS. The address records checked that evening still point at the old VPS.

## Gaps

### Student registration

`POST /api/v1/auth/register` requires an email and sends the verification code inside the same transaction. Every registration fails until mail sends.

- A working SMTP relay in `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD`, and a confirmed `EMAIL_FROM`. The Gmail settings tested from Shahriar's PC are not on this box, and this audit did not put them there. Who owns the sending account is Shahriar's decision.
- A service restart to pick up the new env values (not done here).

Google sign-in is a second student path. It is also off (see below).

### Real mail

- The same SMTP credentials as above.
- The mail audit found no MX, SPF, DKIM, or DMARC for `symamlaw.com` at the time of that audit. Brevo’s ownership TXT, DKIM CNAMEs, and DMARC were added later in Namecheap SimpleDNS. See `docs/exam-site/plans/auth-and-mail.md`.

### `api.symamlaw.com` HTTPS (or whichever hostname Shahriar picks)

- The DNS A record (and optionally AAAA) must point at `187.52.115.111`. Today it points at the old VPS.
- Nginx has no `server_name` for any `symamlaw.com` name. The only HTTPS server is the `srv2089893.hstgr.cloud` block, so a symamlaw request would get the wrong certificate. The HTTP default server returns 404 for any other host.
- A Let's Encrypt certificate for the chosen name, issued after DNS points here.
- `WEB_ORIGIN` is `https://symamlaw.com`. That fits the planned website origin, but the website is not on this box. Next.js and Node are not installed.

### SSLCommerz IPN

- All four `SSLCZ_*` values. The API only creates the SSLCommerz gateway when all four are set. Without them, `POST /api/v1/live-exam-orders` returns 503 `PAYMENT_GATEWAY_UNAVAILABLE`.
- `SSLCZ_CALLBACK_BASE_URL` must be a public HTTPS base on the client's domain. The API builds `…/api/v1/live-exam-orders/{tranId}/return?status=…` and `…/api/v1/live-exam-orders/{tranId}/ipn` from it. That needs the DNS, Nginx, and certificate steps above. This report does not choose the hostname.
- `SSLCZ_BASE_URL` and the store ID and password decide sandbox or live. This report does not choose either. The live store must not be copied from `docs/exam-site/config-files/payment-credentials.md`.
- At least one published live exam with a price, so there is something to order. The DB has 0 categories, 0 live exams, and 0 orders.
- A student who can log in. That needs mail or Google first.

### Google sign-in

- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `GOOGLE_REDIRECT_URI`. The redirect URI must be a public HTTPS URL registered in the Google Cloud console, so it also waits on the hostname.

### Not claimed, but observed

- **Reboot pending.** `/var/run/reboot-required` exists. Uptime is 17 hours. A reboot was not done.
- **No off-server backup.** There is no root or `deploy` crontab and no backup timer. Only the stock `dpkg-db-backup.timer` exists. Client brief section 10 requires off-server database and file backups with a tested restore.
- **Audit log is empty.** `audit_log` has 0 rows, including no row for the Master Admin login. That may be by design (login may not be an audited action); it was not investigated.

## Not read or not checked

- The values in `/etc/coaching-api.env` other than the five non-secret ones listed, and the contents of `/root/master-admin-bootstrap.txt`. Skipped on purpose.
- The PostgreSQL `coaching` role password. Not read.
- A fresh Master Admin login. Not done; the claim is backed by `last_login_at` and the refresh-token row.
- IPv6 HTTPS from outside. The workstation has no IPv6 route.
- The full stack trace of the four `UnsatisfiedDependencyException` startup failures on 23 October. Only the top message was read.
- The old VPS (`client-vps`). Not accessed. Only public DNS was read.
- Hostinger and Namecheap panels. Not accessed.

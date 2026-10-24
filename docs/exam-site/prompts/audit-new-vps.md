# Prompt: Audit the new exam VPS

**Done.** The report is `docs/exam-site/reports/new-vps-provision-audit.md`. Do not run this again. `decisions.md` now records the purchased box and the Gmail SMTP decision.

You are auditing the new Hostinger KVM 2. The box was provisioned on 23 October 2026 and smoke-checked on 24 October 2026. This task is a read-only check of what is actually there. Do not finish the setup. Do not copy mail credentials onto it. Do not change DNS.

Read `docs/exam-site/decisions.md` and `docs/exam-site/client-brief.md` section 10 first. Those files still say the KVM 2 is not purchased. That sentence is stale. The live box wins. Do not edit those files in this task. Record the conflict in the report.

## Outcome

One report, `docs/exam-site/reports/new-vps-provision-audit.md`. It says what was claimed, what you observed, and what is still missing before a student can register and before an SSLCommerz sandbox payment can run.

Stop after the report. Wait for Shahriar.

## Server

SSH alias: `exam-kvm`. Public IPv4: `187.52.115.111`. Hostname: `srv2089893.hstgr.cloud`.

Windows OpenSSH on Shahriar's PC cannot negotiate this host's default key exchange. The `exam-kvm` block in `C:\Users\HP\.ssh\config` sets `KexAlgorithms` to `curve25519-sha256` and the NIST curves. Use that alias. Do not remove the KEX line. If `ssh -o BatchMode=yes exam-kvm` fails, stop and say why.

There is a second login, `deploy`, with the same public key and passwordless sudo. Check it. Do not create another user.

The old site stays on `client-vps` (`88.222.215.187`, `srv633348`). Do not SSH there for this audit unless a public DNS lookup is enough. Do not change that server.

## What the provision claimed

Treat this list as a claim. Verify each line on the box. Mark it **Verified**, **Differs**, or **Not checked**, with the command or request that showed it.

| Claim | Expected |
|---|---|
| OS | Ubuntu 24.04.5 LTS |
| Size | 2 vCPU, about 8 GiB RAM, about 96 GiB disk, 2 GiB swap at `/swapfile` |
| SSH | Root key login. Password login off. `deploy` can sudo |
| Firewall | UFW active. Incoming deny. Allowed: 22, 80, 443 |
| fail2ban | Active |
| Updates | `unattended-upgrades` installed |
| PostgreSQL | 16, database `coaching`, role `coaching`, listening on localhost only |
| Java | OpenJDK 21 JRE. The API runs as `java -jar /opt/coaching-api/api.jar` |
| systemd | `coaching-api.service` enabled, user `deploy`, env file `/etc/coaching-api.env` (mode `640`, group `deploy`) |
| Files | `/var/lib/coaching-api/files` owned by `deploy` |
| Nginx | Proxies `/api/` to `127.0.0.1:8080`. `client_max_body_size 90m`. HTTP for this hostname redirects to HTTPS |
| Certificate | Let's Encrypt for `srv2089893.hstgr.cloud` only. Auto-renew timer present. Expiry was reported as 21 January 2027 |
| Schema | Flyway history reaches version `33` |
| Public read | `GET /api/v1/public/live-exam-categories` returns 200 through HTTPS |
| Auth | No token returns 401 on `/api/v1/me`. Master Admin can log in. Password is only in `/root/master-admin-bootstrap.txt` |
| Mail on this box | `EMAIL_HOST` is a placeholder (`127.0.0.1`). Student `POST /api/v1/auth/register` returns 500 because nothing listens on port 25 |
| Payments | `SSLCZ_*` is unset. Live store was not copied |
| Google | `GOOGLE_*` is unset |
| Website | Next.js is not installed |
| DNS | `symamlaw.com` still points at `88.222.215.187`. This box is reached by `srv2089893.hstgr.cloud` |

A local SMTP test from Shahriar's PC, on 24 October 2026, used the old site's Gmail settings and Gmail accepted one message. Those settings are **not** on this VPS. Do not put them there.

## Hard stops

Do not edit, create, move, or delete files on the server. Do not install packages. Do not stop, restart, reload, or disable services. Do not change Nginx, SSH, firewall, env files, PostgreSQL, certificates, or DNS. Do not issue or renew a certificate. `certbot certificates` is allowed. `certbot renew` is not. `nginx -t` is allowed. `nginx -s reload` is not.

Do not print private keys, database passwords, `JWT_SECRET`, the Master Admin password, mail passwords, or store passwords. You may list env **names** and say whether each value is empty or set. If a command would dump `/etc/coaching-api.env` or `/root/master-admin-bootstrap.txt`, skip the values and note the skip.

Do not register a new student if you already know mail is a placeholder. One failed register is enough only if the log does not already show the mail error. Do not create exams, orders, or users.

Do not open the old VPS env files. Do not copy `docs/exam-site/config-files/payment-credentials.md` onto the server. That file is gitignored and holds live secrets.

## Checks

1. Confirm `ssh -o BatchMode=yes exam-kvm true`. Then `ssh -o BatchMode=yes -o User=deploy exam-kvm sudo -n true`.
2. Record OS, hostname, CPU, RAM, disk, swap, and public IPv4/IPv6. Record what listens on 22, 80, 443, 5432, and 8080, and which address each socket is bound to.
3. Record UFW, fail2ban, and the effective `sshd -T` values for `passwordauthentication` and `permitrootlogin`.
4. Record PostgreSQL version, `listen_addresses`, the database name, and the role name. Do not print the role password. Record the latest Flyway version and the migration count.
5. Record Java version, the systemd unit state, the jar path and owner, and whether the process is listening on `127.0.0.1:8080` only.
6. From the workstation, request `https://srv2089893.hstgr.cloud/` and `https://srv2089893.hstgr.cloud/api/v1/public/live-exam-categories`. Record status codes and whether the certificate name matches. Also request `/api/v1/me` with no token.
7. Read Nginx `server_name`, `client_max_body_size`, and the proxy target. Do not reload Nginx.
8. List which of these env names are set, without values: `SPRING_PROFILES_ACTIVE`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `WEB_ORIGIN`, `FILE_STORAGE_DIR`, `EMAIL_HOST`, `EMAIL_PORT`, `EMAIL_USERNAME`, `EMAIL_PASSWORD`, `EMAIL_FROM`, `SSLCZ_STORE_ID`, `SSLCZ_STORE_PASSWORD`, `SSLCZ_BASE_URL`, `SSLCZ_CALLBACK_BASE_URL`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `MASTER_ADMIN_PHONE`, `MASTER_ADMIN_EMAIL`, `MASTER_ADMIN_NAME`, `MASTER_ADMIN_PASSWORD`.
9. From public DNS, record the A record for `symamlaw.com`, `www.symamlaw.com`, `api.symamlaw.com`, and `srv2089893.hstgr.cloud`. State whether any symamlaw name points at `187.52.115.111`.
10. State what is still required before student registration works, and what is still required before a sandbox SSLCommerz payment can hit this API. Do not choose the callback hostname. Do not choose sandbox versus live.

## Report shape

- Date, SSH alias, hostname, IPv4.
- A claim table: Verified, Differs, or Not checked.
- Listeners and firewall, in short.
- What the public HTTPS checks returned.
- Env names that are set, and names that are empty. No values.
- DNS: this box versus the old VPS.
- Gaps that block student registration, real mail, `api.symamlaw.com` HTTPS, and an SSLCommerz IPN.
- Anything you could not read.

## Stop

Stop after the report. Do not configure mail, deploy the jar again, install Node, point DNS at this box, or write application code.

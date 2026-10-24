# Domain, DNS, and SSL audit

Read-only inspection of the current Hostinger VPS on 23 October 2026, using the `client-vps` SSH alias. No server file was edited. No service was restarted. DNS was not changed. Certificates were not issued or renewed.

Shahriar identified the coaching site as **symamlaw.com** (a coaching center for lawyers). Nginx, certificates, and public DNS match that name. The same VPS also serves other sites. Those stay where they are.

## Executive summary

`symamlaw.com` is registered at Namecheap until 26 February 2027 and is already delegated to Hostinger DNS (`ns1.dns-parking.com`, `ns2.dns-parking.com`). The public A records, including a wildcard, point at this VPS: `88.222.215.187`. There is no AAAA record, no MX, no TXT, no DMARC, and no CAA record.

The coaching site is Nginx in front of two PM2 Next.js apps and a Laravel backoffice. TLS is Let's Encrypt, renewed by Certbot's nginx plugin (HTTP-01) on a systemd timer. `https://symamlaw.com`, `https://admin.symamlaw.com`, and `https://backoffice.symamlaw.com` present valid certificates. `https://www.symamlaw.com` presents the apex certificate, which does not include `www`, so the name check fails. `phpmyadmin.symamlaw.com` is HTTP only.

Java, PostgreSQL, and a Spring Boot process are absent. Port 8080 is closed. The exam API is not on this box.

**Update, 24 October 2026, later.** Nameservers for `symamlaw.com` are now Namecheap SimpleDNS (`dns1.registrar-servers.com`, `dns2.registrar-servers.com`). The Hostinger delegation in the summary below is the 23 October snapshot. Address records checked the same evening still point at `88.222.215.187`.

**Update, 24 October 2026.** The KVM 2 is bought and the API is running. See `decisions.md`. Payment testing stays blocked until the symamlaw A records that should move point at `187.52.115.111` and that name has a valid public certificate. The certificate on the new box covers only `srv2089893.hstgr.cloud`. The callback hostname is still an open choice. Sandbox versus live SSLCommerz credentials stay open. This old VPS expires on 5 November 2026, as previously stated. That date was not re-checked in a Hostinger billing panel.

## Current VPS and website architecture

| Item | Verified value |
|---|---|
| SSH alias | `client-vps` |
| Hostname | `srv633348` (`srv633348.hstgr.cloud`) |
| OS | Ubuntu 24.04.4 LTS, kernel 6.8.0-147-generic |
| Public IPv4 | `88.222.215.187/24` on `eth0` |
| Public IPv6 | `2a02:4780:12:2a40::1/48` on `eth0` |
| CPU / RAM / disk | 2 vCPU, 7.8 GiB RAM (about 5.3 GiB available), 82 GiB free on `/` |
| Swap | none |
| Nginx | 1.24.0, `active` and `enabled`. `nginx -t` succeeded with one warning: protocol options redefined for `0.0.0.0:443` in `sites-enabled/symamlaw.com` |
| Firewall | UFW active. Allowed: OpenSSH and Nginx Full (80 and 443), IPv4 and IPv6 |
| Process managers | systemd; PM2 via `pm2-root.service` (root's nvm, Node v22.14.0); Supervisor for queue workers |
| Docker | not installed |
| Java / PostgreSQL | not installed. Nothing listens on 8080 or 5432 |
| MariaDB | 10.11.14, listening on `127.0.0.1:3306` only |
| PHP | 8.2.35, php8.2-fpm |

Public checks to `88.222.215.187:3000` and `:3306` timed out. The Next.js ports and MariaDB are not reachable from the internet under the current firewall.

Coaching processes:

| App | How it runs | Port | Directory |
|---|---|---|---|
| `symamlaw-frontend` | PM2 id 0, Next.js 14.1.3 | 3000, all interfaces | `/root/symamlaw/symamlaw-frontend` |
| `symamlaw-admin` | PM2 id 1, Next.js 14.2.3 | 3001, all interfaces | `/root/symamlaw/symamlaw-admin` |
| Laravel backoffice | php8.2-fpm | unix socket | `/var/www/html/symamlaw-backend/public` |
| `symamlaw-worker` | Supervisor, user `www-data` | none | `php /var/www/html/symamlaw-backend/artisan queue:work --sleep=3 --tries=3 --max-time=3600` |

Nginx reverse-proxies the two Next.js apps. It serves the Laravel app itself through php-fpm. There is no separate API hostname and no Spring Boot service.

Other applications on this same box, left untouched: `otonest.com` (PM2 `nginx-test`, Next.js 14.2.35, `/srv/otonest/otonest-frontend`, port 3002) and `hatipara-ssh.com` (Laravel under `/var/www/html/nginx-test/hatipara-ss-hospital`). Each has its own Supervisor worker. `/opt` is empty. Nothing named coaching-api is deployed.

## Domain and hostname inventory

Registrar data is from the public Verisign RDAP record. No registrar account was opened.

| Fact | Value |
|---|---|
| Domain | `symamlaw.com` |
| Registrar | NameCheap, Inc. (IANA 1068) |
| Registered | 26 February 2024 |
| Registration expiry | 26 February 2027 |
| Last RDAP change | 4 March 2026 |
| Status | client transfer prohibited |
| DNSSEC | unsigned |
| Nameservers in the registry | `NS1.DNS-PARKING.COM`, `NS2.DNS-PARKING.COM` |

Configured in Nginx for this domain:

| Hostname | Nginx file | Public DNS | HTTPS result on 23 Oct 2026 |
|---|---|---|---|
| `symamlaw.com` | `sites-available/symamlaw.com` | A `88.222.215.187` | 200, certificate name matches |
| `www.symamlaw.com` | same file | A `88.222.215.187` | HTTP 301 to `https://symamlaw.com/`. HTTPS fails the name check (`SEC_E_WRONG_PRINCIPAL`) |
| `admin.symamlaw.com` | `sites-available/admin.symamlaw.com` | A `88.222.215.187` | 307 to `/admin/dashboard`, certificate name matches |
| `backoffice.symamlaw.com` | `sites-available/backoffice.symamlaw.com` | A `88.222.215.187` | 200, certificate name matches |
| `phpmyadmin.symamlaw.com` | `sites-available/phpmyadmin.symamlaw.com` | A `88.222.215.187` | HTTP 200. No TLS server block, so HTTPS does not complete |

No Nginx `server_name` exists for `api`, `exam`, or `staging`. A wildcard answers those names anyway (next section). HTTP for an unknown name (`zz-audit-check-not-real.symamlaw.com`) returns the default site, `/var/www/html` (the packaged Nginx page), not the Next.js app.

Neighbor zones on this IP, not part of the coaching move: `otonest.com` (and `www`, `dev`, `archive`, `dev-backoffice`) and `hatipara-ssh.com` (and `www`). Their A records also point at `88.222.215.187`.

## DNS record inventory and current destinations

Lookups used Cloudflare DNS over HTTPS on 23 October 2026. A-record TTL is 14400 seconds (4 hours) unless noted.

| Name | Type | Destination | TTL |
|---|---|---|---|
| `symamlaw.com` | A | `88.222.215.187` | 14400 |
| `symamlaw.com` | AAAA | none | |
| `*.symamlaw.com` | A | `88.222.215.187` | 14400 |
| `www.symamlaw.com` | A | `88.222.215.187` | 14400 |
| `admin.symamlaw.com` | A | `88.222.215.187` | 14400 |
| `backoffice.symamlaw.com` | A | `88.222.215.187` | 14400 |
| `phpmyadmin.symamlaw.com` | A | `88.222.215.187` | 14400 |
| `symamlaw.com` | NS | `ns1.dns-parking.com`, `ns2.dns-parking.com` | 86400 |
| `symamlaw.com` | SOA | primary `ns1.dns-parking.com`, admin `dns.hostinger.com`, serial `2026102201`, refresh 10000, retry 2400, expire 604800, minimum 600 | 3600 |
| `symamlaw.com` | MX | none | |
| `symamlaw.com` | TXT | none | |
| `symamlaw.com` | CAA | none | |
| `_dmarc.symamlaw.com` | TXT | none | |

The wildcard is a real record: a direct query for `*.symamlaw.com` returns `88.222.215.187`. Any name under the zone that has no more specific record follows this VPS. `api.symamlaw.com` resolves to this VPS and has no application behind it.

No CNAME was returned for the coaching names above. The address matches the VPS address exactly, so the names are not behind a CDN proxy.

## DNS provider and nameserver findings

The domain is registered at Namecheap. Authoritative DNS is Hostinger's parking nameservers, and the SOA admin mailbox is `dns.hostinger.com`. The zone is edited in the Hostinger DNS panel for this domain, unless the nameservers are later changed at Namecheap.

Public DNS does not show which person can log in. A Namecheap login that only shows these two nameservers cannot edit the A records. The Hostinger account that holds the zone can.

`otonest.com` uses the same `dns-parking.com` pair. `hatipara-ssh.com` uses `helios.dns-parking.com` and `aster.dns-parking.com`, also Hostinger. Those are separate zones. This migration edits only `symamlaw.com`.

## SSL certificate inventory and hostname coverage

Certbot 2.9.0. All certificates are Let's Encrypt ECDSA. Private-key paths are listed so the new server can be given its own key. Key files were not read.

Coaching certificates:

| Cert name | Covers | Issuer | Not after | Full chain | Private key |
|---|---|---|---|---|---|
| `symamlaw.com-0001` | `symamlaw.com` only | Let's Encrypt YE2 | 16 January 2027 | `/etc/letsencrypt/live/symamlaw.com-0001/fullchain.pem` | `/etc/letsencrypt/live/symamlaw.com-0001/privkey.pem` |
| `admin.symamlaw.com` | `admin.symamlaw.com` | Let's Encrypt YE1 | 30 November 2026 | `/etc/letsencrypt/live/admin.symamlaw.com/fullchain.pem` | `/etc/letsencrypt/live/admin.symamlaw.com/privkey.pem` |
| `backoffice.symamlaw.com` | `backoffice.symamlaw.com` | Let's Encrypt YE1 | 30 November 2026 | `/etc/letsencrypt/live/backoffice.symamlaw.com/fullchain.pem` | `/etc/letsencrypt/live/backoffice.symamlaw.com/privkey.pem` |

`www.symamlaw.com` and `phpmyadmin.symamlaw.com` have no certificate of their own. The HTTPS `www` server block uses `symamlaw.com-0001`, and the live handshake rejects that name.

Public `curl` to `https://symamlaw.com`, `https://admin.symamlaw.com`, and `https://backoffice.symamlaw.com` reported `ssl_verify` 0. Chain and hostname match for those three.

Neighbor certificates on this box (do not copy, revoke, or renew as part of this work): `otonest.com` (includes `www`, expires 23 December 2026), `dev.otonest.com` (26 November 2026), `archive.otonest.com` (23 November 2026), `dev-backoffice.otonest.com` (23 December 2026), `hatipara-ssh.com` (includes `www`, 11 December 2026), and an extra `www.hatipara-ssh.com` certificate (11 December 2026) that the active Nginx file does not reference. Nginx for `hatipara-ssh.com` uses the combined certificate.

## Certificate renewal and validation details

Every renewal file under `/etc/letsencrypt/renewal/` has `authenticator = nginx`, `installer = nginx`, and `server = https://acme-v02.api.letsencrypt.org/directory`. No `webroot` line and no DNS-plugin line. That is HTTP-01 through the Nginx plugin. No DNS API credential file is referenced.

Renewal is scheduled by `certbot.timer`: twice a day (`00:00` and `12:00`) with up to 12 hours of random delay. The service runs `certbot -q renew`. It was not run during this audit.

TLS settings come from `/etc/letsencrypt/options-ssl-nginx.conf`, included by the coaching HTTPS blocks: TLSv1.2 and TLSv1.3, session tickets off. The coaching responses do not send HSTS. `otonest.com` does (`max-age=31536000; includeSubDomains`). That header is on a neighbor vhost.

HTTP listeners for `symamlaw.com`, `www`, `admin`, and `backoffice` return 301 to HTTPS. The `www` HTTP redirect target is `https://symamlaw.com`. The `www` HTTPS block is also a redirect to the apex, and clients that already use HTTPS fail the certificate check before they follow it.

`nginx.conf` and the three main coaching vhosts set no `client_max_body_size`. The packaged default is 1 megabyte. `phpmyadmin.symamlaw.com` sets 50m. A written-answer PDF on the new API has an 80 megabyte ceiling, so the new server's upload location needs a limit above that. This audit did not change the current files.

## Existing Nginx configuration relationships

Enabled coaching files:

- `/etc/nginx/sites-enabled/symamlaw.com` → proxy to `http://127.0.0.1:3000` for the apex. `www` on port 443 redirects to the apex. Port 80 redirects both names to `https://symamlaw.com`.
- `/etc/nginx/sites-enabled/admin.symamlaw.com` → proxy to `http://localhost:3001`. Port 80 redirects to HTTPS.
- `/etc/nginx/sites-enabled/backoffice.symamlaw.com` → root `/var/www/html/symamlaw-backend/public`, php8.2-fpm. Port 80 redirects to HTTPS.
- `/etc/nginx/sites-enabled/phpmyadmin.symamlaw.com` → root `/usr/share/phpmyadmin`, port 80 only, php8.2-fpm.
- `/etc/nginx/sites-enabled/default` → port 80 `default_server`, root `/var/www/html`, `server_name _`. There is no TLS default server.

`symamlaw.com` listens with `http2`. The other coaching TLS blocks do not. That mismatch is the `nginx -t` warning. Config test still passed.

## Email and other DNS records that must be preserved

`symamlaw.com` has no MX, SPF, DKIM, or DMARC records. Moving the website A records does not move mail, because mail is not published in this zone.

Leave these alone:

- The whole `otonest.com` zone. It has MX `1 SMTP.GOOGLE.COM` and several names on this VPS.
- The whole `hatipara-ssh.com` zone. It has no MX. Its website still points at this VPS.
- The Hostinger nameservers for `symamlaw.com`, unless a later decision moves DNS on purpose. An A-record change does not require a nameserver change.

There is no CAA record to preserve. An empty CAA set also means Let's Encrypt is allowed to issue.

## Migration risks and dependencies

Registration, DNS, Nginx, and the certificate are four separate layers. Namecheap holds the registration until 26 February 2027. Hostinger's nameservers hold the zone. Nginx on this VPS maps hostnames to apps. Let's Encrypt certificates are files on this VPS, presented by Nginx. The new KVM 2 needs its own Nginx and its own certificate. The registration stays at Namecheap. The zone can stay at Hostinger while individual A records change.

1. Records that must change for the new box: the A record for each coaching name that should be served there. The wildcard `*.symamlaw.com` also points at this VPS. Leaving it in place sends every other subdomain, including a future `api` name, to the old server. Pointing the wildcard at the new server sends every unpublished name there too. Explicit A records for the chosen names are the controlled option. No AAAA record exists today, so IPv6 clients are not using DNS to reach this box. Add an AAAA only when the new box should answer IPv6.
2. Records that stay: no mail records exist on `symamlaw.com`. Neighbor zones stay on their current addresses. This VPS still hosts those sites, and it expires on 5 November 2026. Their move is outside this audit.
3. The current certificate files could be copied onto another machine and would verify until they expire. That is a poor default. Renewal is tied to this server's Nginx plugin, the apex certificate omits `www`, and each name has its own certificate.
4. Issue a fresh Let's Encrypt certificate on the new box for the exact set of names that box will serve. Include `www` if `www` remains in use.
5. The current method is HTTP-01. That challenge reaches whichever server the A record points at. A certificate can be issued on the new box before the cutover by using DNS-01, or immediately after the A record moves by using HTTP-01. No CAA record and no CDN proxy block either method.
6. Hostname coverage is enough for the current three HTTPS sites, and not enough for `www` or for an API name. No `api` server block exists. SSLCommerz still needs a public HTTPS URL on `symamlaw.com`. This audit does not choose that hostname. Names that already have a matching certificate are `symamlaw.com`, `admin.symamlaw.com`, and `backoffice.symamlaw.com`.
7. Nothing in DNS prevents a new Let's Encrypt certificate. The wildcard will keep answering names that do not get an explicit record.
8. The new VPS can be tested before public DNS changes, by its own hostname `https://srv2089893.hstgr.cloud` or by a hosts-file override. The KVM 2 is purchased. Public DNS for `symamlaw.com` has not moved.
9. Low downtime is available. Bring the new site up, lower the A-record TTL ahead of the change (it is 4 hours now), then change only the chosen symamlaw A records. Leave this Nginx running until the VPS expires so the old site can still answer.
10. Access still required is listed in the next section.

`phpmyadmin.symamlaw.com` is a public HTTP site on the coaching domain. The new exam server should not publish it. Course and book payments are out of this delivery. They were not configured as SSLCommerz on this VPS, and this audit did not open application environment files.

The current site's data (Laravel, MariaDB, uploaded files) was not inventoried. Whether any of it must be copied is still an open question. No migration is assumed.

## Exact information or access needed from the client

- The Hostinger account that can edit the `symamlaw.com` zone. Namecheap access is needed only if the nameservers themselves should change. This audit did not log into either account.
- Which hostnames move on cutover day: apex, `www`, `admin`, `backoffice`, and whether a new API name is created. The SSLCommerz callback or IPN hostname is one of those choices and is still open.
- Whether the SSLCommerz credentials for the first test are sandbox or live. Values stay in environment variables on the new server. They do not belong in this report.
- Whether any data from the current Laravel site must be migrated. The replacement stack (Next.js, Spring Boot, PostgreSQL) is not running here.
- Purchase of the Hostinger KVM 2. Done. The box is `187.52.115.111` (`srv2089893.hstgr.cloud`). The API is deployed. The website is not. Public DNS has not moved.
- Confirmation that `otonest.com` and `hatipara-ssh.com` are someone else's problem before 5 November 2026. This audit will not move them.

## Recommended migration sequence

1. Keep this report as the record of the old box.
2. Shahriar confirms the hostname list, the callback hostname, and the Hostinger DNS login.
3. The KVM 2 is purchased and the API is provisioned. The website is not deployed. Do not point public DNS at the new box yet.
4. Lower the TTL on the symamlaw A records that will change. The SOA minimum is already 600 seconds. The address records are 14400.
5. Issue a new Let's Encrypt certificate on the new box for the confirmed names. DNS-01 can happen before the A records move. HTTP-01 waits until they do.
6. Set the new Nginx upload limit high enough for an 80 megabyte PDF on the API location. Redirect HTTP to HTTPS. Include `www` on the certificate if `www` still resolves.
7. Change only the confirmed `symamlaw.com` A records to the new IPv4 address. Leave the neighbor zones, and leave this server's Nginx, in place.
8. Verify the checklist below. Start SSLCommerz payment testing only after the callback URL is public HTTPS on the new box.
9. Keep this VPS serving the old symamlaw vhosts until 5 November 2026 so a DNS rollback still has a target.

## Rollback strategy

Rollback is a DNS edit: set the same symamlaw A records back to `88.222.215.187`. The old Nginx, applications, and certificates stay running until the VPS expires on 5 November 2026. There are no mail records to put back. After that expiry date this server is gone and a DNS rollback has nowhere to land. Neighbor sites are not part of the rollback, because their records are not part of the change.

## Pre-migration and post-migration verification checklist

Before any DNS edit:

- [x] KVM 2 is purchased. The API answers at `https://srv2089893.hstgr.cloud`. The Next.js site is not deployed yet.
- [ ] The new certificate covers every hostname that will be switched, including `www` if `www` is in the set.
- [ ] HTTP on the new box redirects to HTTPS.
- [ ] The API upload location accepts a body larger than 80 megabytes.
- [ ] The chosen callback hostname is one of the names on that certificate.
- [ ] TTL on the records about to change has been lowered and the old TTL has expired.
- [ ] A written list of the exact A records to edit exists, and `otonest.com` and `hatipara-ssh.com` are absent from it.
- [ ] The wildcard decision is explicit: leave `*.symamlaw.com` on the old IP, or replace it with explicit records.

After the DNS edit:

- [ ] `symamlaw.com` A record is the new IPv4. AAAA is still absent, unless IPv6 was intentionally added.
- [ ] `https://symamlaw.com` returns 200 with a certificate whose SAN includes that name.
- [ ] `https://www.symamlaw.com` either redirects with a matching certificate or is no longer in DNS.
- [ ] Each moved subdomain presents its own matching certificate.
- [ ] An unmoved name, including a random name if the wildcard stayed on the old IP, still hits the intended server.
- [ ] `otonest.com` and `hatipara-ssh.com` still resolve to `88.222.215.187` and still answer.
- [ ] The SSLCommerz callback URL is fetched over HTTPS from the public internet before any live payment.
- [ ] Rollback is still possible: the old vhost answers when the A record is pointed back at `88.222.215.187`.

Stop here. No migration, DNS edit, certificate request, or VPS purchase was performed.

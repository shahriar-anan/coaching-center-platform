# Domain and DNS access audit

Collected on 23 October 2026. Read-only. DNS was not edited. Nameservers were not changed. Nginx, certificates, and services on the VPS were not modified. Private keys, passwords, tokens, and mailbox local-parts were not recorded.

**Update, 24 October 2026.** The replacement VPS now exists: `187.52.115.111`, `srv2089893.hstgr.cloud`. The "none yet" line below is the state on the collection date. `symamlaw.com` address records were still on the old VPS when this note was added. See `decisions.md`.

**Update, 24 October 2026, later.** Shahriar moved this domain’s DNS from Hostinger to Namecheap SimpleDNS. Public NS are now `dns1.registrar-servers.com` and `dns2.registrar-servers.com`. The Hostinger `dns-parking.com` findings below are the 23 October snapshot. Later edits go in Namecheap Advanced DNS. A fresh lookup the same evening still has the apex, `www`, `api`, `admin`, and `backoffice` on `88.222.215.187`. Brevo’s ownership TXT, both DKIM CNAMEs, and `_dmarc` `p=none` are present in the new zone. The neighbor zones `otonest.com` and `hatipara-ssh.com` were not re-checked.

Labels used below:

- **Verified** — observed in public DNS, registry data, a live request, or a file read on the VPS.
- **Inferred** — follows from those observations, with the basis stated.
- **Needs confirmation** — not visible with the access used here.

## 1. Current situation

`symamlaw.com` is registered at Namecheap and delegated to Hostinger DNS. The website address records that can be seen publicly point at the existing VPS, `88.222.215.187`. The DNS zone is not stored on that VPS. SSH can inspect the web server. It cannot list or edit the Hostinger zone.

| Fact | Value | Label |
|---|---|---|
| Domain | `symamlaw.com` | **Verified** |
| Registrar | NameCheap, Inc. (IANA 1068), from Verisign RDAP | **Verified** |
| Registration | 26 February 2024 | **Verified** |
| Registration expiry | 26 February 2027 | **Verified** |
| Registry last change | 4 March 2026 | **Verified** |
| Registry status | `client transfer prohibited` | **Verified** |
| DNSSEC | unsigned (`delegationSigned: false`) | **Verified** |
| Registry nameservers | `NS1.DNS-PARKING.COM`, `NS2.DNS-PARKING.COM` | **Verified** |
| SOA primary | `ns1.dns-parking.com` | **Verified** |
| SOA responsible mailbox | `dns.hostinger.com` | **Verified** |
| SOA serial | `2026102201` | **Verified** |
| SOA timers | refresh 10000, retry 2400, expire 604800, minimum 600; SOA TTL 3600 | **Verified** |
| Apex A | `88.222.215.187`, TTL 14400 | **Verified** |
| VPS hostname | `srv633348.hstgr.cloud` | **Verified** |
| VPS OS | Ubuntu 24.04.4 LTS | **Verified** |
| VPS IPv4 | `88.222.215.187` | **Verified** by DNS target and by the site answering from that host |
| VPS IPv6 on `eth0` | `2a02:4780:12:2a40::1/48` | **Verified** on the server. No public AAAA uses it |
| VPS service end | 5 November 2026 | **Needs confirmation**. Stated for this audit. Not visible over SSH |
| New VPS address | none yet | **Needs confirmation**. The replacement server has not been purchased |

The authoritative nameservers answer at `162.159.24.201` (`ns1.dns-parking.com`) and `162.159.25.42` (`ns2.dns-parking.com`). Reverse DNS for those addresses is the same two names. Those addresses are the DNS servers. They are a different machine from the website VPS.

A zone transfer (`AXFR`) to `ns1.dns-parking.com` returned “Not implemented”. The VPS runs `systemd-resolved` on `127.0.0.53` and `127.0.0.54` only. No public DNS service and no `symamlaw.com` zone file were found on it.

**Inferred:** Hostinger operates the live zone. The evidence is the nameserver hostnames, the SOA mailbox `dns.hostinger.com`, and Namecheap’s registry delegation to those nameservers. Which Hostinger login owns the zone was not visible.

The serial `2026102201` is the value in the zone. `otonest.com` and `hatipara-ssh.com` currently publish the same serial. Treating `20261022` as an edit date is a common serial convention, and the shared number means it is weak evidence that this zone alone was edited on 22 October 2026. **Needs confirmation** what, if anything, changed in the Hostinger panel.

## 2. Verified DNS records and hostnames

Lookups below are from the authoritative server `ns1.dns-parking.com`, or from Cloudflare DNS-over-HTTPS when the record type was CAA or a wildcard name. TTLs are the values those servers returned, in seconds.

### Records with their own owner name

A wildcard does not cover the zone apex, so the apex address is its own record. A direct query for `*.symamlaw.com` returned an A record owned by that wildcard name, so the wildcard exists as a record.

| Name | Type | Data | TTL | Label |
|---|---|---|---|---|
| `symamlaw.com` | A | `88.222.215.187` | 14400 | **Verified** |
| `symamlaw.com` | NS | `ns1.dns-parking.com`, `ns2.dns-parking.com` | 86400 | **Verified** at the authoritative server and via DoH |
| `symamlaw.com` | SOA | primary `ns1.dns-parking.com`, mailbox `dns.hostinger.com`, serial `2026102201` | 3600 | **Verified** |
| `*.symamlaw.com` | A | `88.222.215.187` | 14400 | **Verified** |

The workstation resolver also returned the NS set with TTL 21600. That is a cached remainder. The zone’s NS TTL, read from `ns1.dns-parking.com`, is 86400.

### Names that resolve, without proof they are separate records

Every name below returned `A 88.222.215.187` with TTL 14400, and no CNAME. The same answer is what the wildcard produces. Public DNS therefore shows where they go. It does not show whether Hostinger stores each one as its own A record.

| Name | Public answer | Nginx `server_name` | Label for “explicit record?” |
|---|---|---|---|
| `www.symamlaw.com` | A `88.222.215.187` | yes, in `sites-available/symamlaw.com` | **Needs confirmation** |
| `admin.symamlaw.com` | A `88.222.215.187` | yes | **Needs confirmation** |
| `backoffice.symamlaw.com` | A `88.222.215.187` | yes | **Needs confirmation** |
| `phpmyadmin.symamlaw.com` | A `88.222.215.187` | yes, port 80 only | **Needs confirmation** |
| `api.symamlaw.com` | A `88.222.215.187` | no | **Needs confirmation** |
| `mail`, `webmail`, `ftp`, `smtp`, `imap`, `pop`, `autodiscover`, `cpanel` | A `88.222.215.187` | no | **Needs confirmation**. No vhost. Consistent with the wildcard |
| `ns1.symamlaw.com`, `ns2.symamlaw.com` | A `88.222.215.187` | no | **Needs confirmation**. These are not the zone’s nameservers |
| `wildcard-probe-zx9k.symamlaw.com` | A `88.222.215.187` | no | **Verified** as wildcard behavior. This name was chosen because it should not exist as its own record |
| `_dmarc.symamlaw.com` | A `88.222.215.187`; TXT empty | no | A answer is wildcard-consistent. TXT absence is **Verified** |
| `default._domainkey`, `google._domainkey`, `hostinger._domainkey` | TXT empty | no | **Verified** absent at those names. This is not an exhaustive DKIM search |

`a.b.symamlaw.com` and `a.b.c.symamlaw.com` also returned `A 88.222.215.187` TTL 14400. A query for the literal name `*.*.symamlaw.com` returned NXDOMAIN. **Needs confirmation** how the Hostinger zone makes multi-label names resolve. A normal one-label wildcard does not, by itself, explain `a.b.symamlaw.com`. The panel or a zone export is the missing source.

### Record types that are absent

Empty answers below came back as NOERROR with the SOA in the authority section (the negative-cache TTL on those responses was 600). CAA was queried as type 257. Apex SRV was queried as type 33.

| Query | Result | Label |
|---|---|---|
| `symamlaw.com` AAAA | none | **Verified** |
| `*.symamlaw.com` AAAA | none | **Verified** |
| `symamlaw.com` MX | none | **Verified** |
| `symamlaw.com` TXT | none. No SPF record | **Verified** |
| `symamlaw.com` CAA | none | **Verified** |
| `symamlaw.com` SRV | none | **Verified** |
| `www.symamlaw.com` CNAME | none | **Verified** |
| `_dmarc.symamlaw.com` TXT | none | **Verified** |

No CDN hostname appeared in front of the A records. The published address is the VPS address.

### What must be preserved is only partly visible

**Verified** public behavior to preserve, if the current answers are to stay the same:

- Apex A to the website address.
- The one-label wildcard A to that same address, plus whatever extra behavior makes deeper names resolve.
- NS delegation, until someone deliberately changes it.
- The absence of MX, TXT, CAA, and AAAA. There is no mail, SPF, DMARC, or IPv6 address record to copy.

**Needs confirmation** before anyone recreates the zone: the Hostinger panel’s full list. Wildcard answers hide any explicit A record that uses the same address and TTL. A nameserver move does not copy records. Namecheap’s own article says host records are not added to the new nameservers automatically ([How to Change DNS For a Domain](https://www.namecheap.com/support/knowledgebase/article.aspx/767/10/how-to-change-dns-for-a-domain/)).

## 3. Nginx and SSL

Enabled coaching files:

| File | `server_name` | Where it sends traffic | Label |
|---|---|---|---|
| `sites-enabled/symamlaw.com` | `symamlaw.com`, `www.symamlaw.com` | Port 80 redirects both names to `https://symamlaw.com`. Port 443 for the apex proxies to `http://127.0.0.1:3000`. Port 443 for `www` redirects to `https://symamlaw.com` and presents the apex certificate | **Verified** |
| `sites-enabled/admin.symamlaw.com` | `admin.symamlaw.com` | Port 443 proxies to `http://localhost:3001`. A live HTTP request returned 301 to `https://admin.symamlaw.com/` | **Verified** |
| `sites-enabled/backoffice.symamlaw.com` | `backoffice.symamlaw.com` | Root `/var/www/html/symamlaw-backend/public`, PHP 8.2-FPM. A live HTTP request returned 301 to HTTPS. `/documents/` allows framing from `https://symamlaw.com` | **Verified** |
| `sites-enabled/phpmyadmin.symamlaw.com` | `phpmyadmin.symamlaw.com` | Port 80 only. Root `/usr/share/phpmyadmin`. `client_max_body_size 50M`. No TLS block | **Verified** |
| `sites-enabled/default` | `_` | Port 80 `default_server`, root `/var/www/html`. The TLS `default_server` lines are commented out | **Verified** |

`sites-available/symamlaw.com.bak` and `sites-available/backup_symamlawcom` exist and are not enabled. **Verified**.

No enabled `server_name` contains `api`, `exam`, `staging`, `mail`, or `webmail`. **Verified**.

The admin and backoffice port 80 blocks contain Certbot’s `if ($host = …) return 301` and a later `return 404`. Live requests with the matching Host received the 301. **Verified**.

### Live HTTP and HTTPS on 23 October 2026

| URL | Result | Certificate presented | Label |
|---|---|---|---|
| `https://symamlaw.com/` | 200, name check passed | `CN=symamlaw.com`, Let’s Encrypt YE2, 18 Oct 2026 – 16 Jan 2027 | **Verified** |
| `http://symamlaw.com/` | 301 to `https://symamlaw.com/` | | **Verified** |
| `http://www.symamlaw.com/` | 301 to `https://symamlaw.com/` | | **Verified** |
| `https://www.symamlaw.com/` | Name check failed (`SEC_E_WRONG_PRINCIPAL`). With checks ignored, Nginx returns 301 to the apex | Apex certificate `CN=symamlaw.com`, which covers `symamlaw.com` | **Verified** |
| `https://admin.symamlaw.com/` | 307 to `/admin/dashboard`, name check passed | `CN=admin.symamlaw.com`, Let’s Encrypt YE1, 1 Sep 2026 – 30 Nov 2026 | **Verified** |
| `http://admin.symamlaw.com/` | 301 to HTTPS | | **Verified** |
| `https://backoffice.symamlaw.com/` | 200, name check passed | `CN=backoffice.symamlaw.com`, Let’s Encrypt YE1, 1 Sep 2026 – 30 Nov 2026 | **Verified** |
| `http://backoffice.symamlaw.com/` | 301 to HTTPS | | **Verified** |
| `http://phpmyadmin.symamlaw.com/` | 200, HTML title `phpMyAdmin` | none on port 80 | **Verified** |
| `https://phpmyadmin.symamlaw.com/` | Name check failed. With checks ignored, response is 307 to `/admin/dashboard` | `CN=admin.symamlaw.com` | **Verified** |
| `http://api.symamlaw.com/` | 200, title `Welcome to nginx!` | | **Verified**. This is the port 80 default site |
| `https://api.symamlaw.com/` | Name check failed. With checks ignored, response is 307 to `/admin/dashboard` | `CN=admin.symamlaw.com` | **Verified** |
| `http://wildcard-probe-zx9k.symamlaw.com/` | 200, title `Welcome to nginx!` | | **Verified** |
| `https://wildcard-probe-zx9k.symamlaw.com/` | 307 to `/admin/dashboard` when the name check is ignored | same fallback as `api` | **Verified** |

**Inferred:** the first TLS server Nginx selected for an unmatched name is the admin vhost. `api`, `phpmyadmin`, and the random probe all received the admin certificate and the admin application’s redirect. `sites-enabled/admin.symamlaw.com` is the first enabled file in alphabetical order. The directory order was not confirmed with `nginx -T`.

Certbot on the server lists the same three coaching certificates and the same expiry times. Renewal files for those names set `authenticator = nginx` and `installer = nginx` against `https://acme-v02.api.letsencrypt.org/directory`. No webroot path and no DNS authenticator are set. **Verified**. Renewal is HTTP-01 through Nginx, on `certbot.timer` (`00:00` and `12:00` with up to 12 hours of delay). The timer is enabled and active. It was not run during this audit.

`www` has no certificate of its own. `phpmyadmin` has no live certificate. Older Certbot backup directories are named `phpmyadmin.symamlaw.com`. **Verified** that those directory names exist, and that `certbot certificates` does not list a current phpMyAdmin certificate. **Needs confirmation** when that certificate was removed.

Private key files were not read.

### Application hostnames the coaching apps use

Read from environment files. Secret values were skipped.

| Setting | Value | Label |
|---|---|---|
| Laravel `APP_URL` and `ASSET_URL` | `https://backoffice.symamlaw.com` | **Verified** |
| Laravel `FRONTEND_URL` | `https://symamlaw.com/` | **Verified** |
| Frontend `NEXTAUTH_URL` | `https://symamlaw.com` | **Verified** in `.env` and `.env.save` |
| Admin `NEXT_PUBLIC_API_URL` and `API_URL` | `https://backoffice.symamlaw.com/api/` | **Verified** |
| `SSLCZ_TESTMODE` | `false` | **Verified**. The config then selects `https://securepay.sslcommerz.com` |
| `MAIL_MAILER` | `smtp` | **Verified** |
| `MAIL_HOST` | `smtp.gmail.com` port 587, encryption `tls` | **Verified** |
| `MAIL_FROM_ADDRESS` | an address at `symamlaw.com` | **Verified** domain only |
| `MAIL_USERNAME` | an address at `gmail.com` | **Verified** domain only |

The SSLCommerz library sets the success, fail, and cancel URLs to `url('/')` plus `/purchase/success`, `/purchase/fail`, and `/purchase/cancel`. Those three paths are `POST` routes on the Laravel app. **Inferred:** a payment started while `url('/')` follows `APP_URL` calls back to `https://backoffice.symamlaw.com/purchase/…`. The IPN field is set only when the caller passes `ipn_url`. This audit did not follow every caller. **Needs confirmation** whether live payments send an IPN, and which URL is stored in the SSLCommerz merchant panel.

`APP_ENV=local` is set on this server. That is an application setting, separate from DNS.

## 4. Access and what each login can change

| Access | What it can do for this domain | What it cannot do | Label |
|---|---|---|---|
| `client-vps` SSH | Read Nginx, certificates, and application URL settings. The web server on this VPS can be changed from this login. That was not done | Edit public DNS. Export the Hostinger zone. See the Hostinger billing date. See the Namecheap account | **Verified** for the DNS limit: no zone file and no public DNS listener on the VPS. AXFR is not implemented |
| Hostinger DNS panel for this zone | Edit the live A, wildcard, and any other records while the registry still delegates to `dns-parking.com` | This audit had no Hostinger login. The panel contents were not seen | **Needs confirmation** which person can open it. Shahriar stated the client does not have the Hostinger account for this VPS |
| Namecheap account | Change the domain’s nameservers. Registration runs until 26 February 2027. The client has been stated to have this login | Edit the live host records while the nameservers stay on `dns-parking.com`. Namecheap documents that host records in the account apply when the domain uses Namecheap BasicDNS, and that a nameserver switch does not copy the old records | **Verified** for the current delegation. **Needs confirmation** for the on-screen Namecheap message; this audit did not open the account. The report matches Namecheap’s published rule |
| Namecheap BasicDNS, if selected later | Nameservers published by Namecheap for that option: `dns1.registrar-servers.com` and `dns2.registrar-servers.com`. After those become the registry nameservers, host records are edited in Namecheap Advanced DNS | It does not import the Hostinger zone. Every record that should survive has to be created there from a known list. This audit did not switch anything | **Verified** as Namecheap’s documented nameservers and copy behavior. The full record list required for a faithful copy is **Needs confirmation** |
| Zone export without a Hostinger login | No path found | AXFR refused. Nothing on the VPS is a copy of the zone | **Verified** |

`otonest.com` and `hatipara-ssh.com` use Hostinger DNS as well, and they are separate zones. The same generic mailbox `dns.hostinger.com` appears in all three SOAs. **Needs confirmation** whether one Hostinger customer login controls all three zones. Shared nameserver brands do not prove a shared login.

## 5. Risks and dependencies

These are dependencies that are already true. They are not a cutover plan.

1. **The live website address is the Hostinger zone.** **Verified.** Visitors reach `88.222.215.187` because that zone publishes the A record and the wildcard. Nginx on the VPS only matters after DNS has chosen that address. SSH downtime and DNS downtime are different failures.

2. **Several hostnames are in active use.** **Verified.** The apex, `www` (HTTP, then a broken HTTPS name check), `admin`, and `backoffice` answer as applications. The admin app calls `https://backoffice.symamlaw.com/api/`. The backoffice allows document framing from `https://symamlaw.com`. Payment return paths are built on the Laravel root. Losing `backoffice` or the apex address takes the current site and the current payment return path with it.

3. **`www` HTTPS fails the name check today.** **Verified.** HTTP `www` redirects to the apex. HTTPS `www` presents a certificate that covers `symamlaw.com`. Clients that verify the name stop before the redirect.

4. **Unmatched HTTPS names fall through to the admin app.** **Verified** for `api`, `phpmyadmin`, and a random name. There is no `api` vhost. The wildcard still points those names at this server. A client that ignores the certificate error reaches the admin application.

5. **phpMyAdmin is on the public internet over HTTP.** **Verified.** It has a vhost and returned its login page. Whether anyone still uses it is **Needs confirmation**.

6. **Inbound mail is not configured. Outbound mail does not use an MX record.** **Verified:** no MX, no apex TXT, no DMARC TXT, and no listeners on ports 25, 465, 587, 993, 995, or 143. The app submits mail to `smtp.gmail.com` and uses a From address at `symamlaw.com`. Changing the website A record does not move an MX, because there is no MX. **Needs confirmation** whether anyone expects to receive mail at `@symamlaw.com`.

7. **Let’s Encrypt renewal on this box is HTTP-01.** **Verified.** The challenge arrives at whichever server the A record points at, on port 80, for the name on the certificate. The certificates themselves are files on this VPS. `admin` and `backoffice` expire on 30 November 2026. The apex certificate expires on 16 January 2027.

8. **The same VPS hosts other sites. Their DNS is separate.** **Verified.**

| Zone | Nameservers | Apex A | Mail | Also on this Nginx |
|---|---|---|---|---|
| `otonest.com` | `ns1.dns-parking.com`, `ns2.dns-parking.com` | `88.222.215.187` TTL 14400 | MX `1 SMTP.GOOGLE.COM` TTL 3600 | `otonest.com`, `www`, `dev`, `archive`, `dev-backoffice` |
| `hatipara-ssh.com` | `helios.dns-parking.com`, `aster.dns-parking.com` | `88.222.215.187` TTL 14400 | no MX | `hatipara-ssh.com`, `www` |

Both neighbor zones also answer `*.<domain>` and a random single-label name with `A 88.222.215.187`. Their SOA mailbox is `dns.hostinger.com`, serial `2026102201`.

A change to `symamlaw.com` records, or a change to `symamlaw.com` nameservers at Namecheap, leaves the other two zones where they are. **Verified** as separate DNS zones. Both neighbor sites still stop working if this VPS or this IP goes away, because their A records point here and their vhosts run here. **Verified.** Their registrars were not looked up.

9. **No new address exists yet.** The replacement VPS has not been purchased, so there is no second IP to test against these records.

10. **The stated VPS end date is 5 November 2026.** **Needs confirmation** in Hostinger billing. If that date is right, this IP stops serving every zone that points at it, including `otonest.com` and `hatipara-ssh.com`, whether or not `symamlaw.com` DNS was edited.

## 6. Unanswered questions

Each one blocks a reliable choice between leaving the zone at Hostinger and moving it to Namecheap BasicDNS.

1. **The full Hostinger zone for `symamlaw.com`.** A screenshot or export of every record, including which names are explicit and which exist only because of the wildcard, and the setting that makes `a.b.symamlaw.com` resolve. Public queries cannot separate an explicit A record from the wildcard when the address and TTL match. A copied zone can miss a record that is invisible from outside.

2. **Which Hostinger login can edit that zone.** In-place record changes require it. SSH does not. The client has been stated not to have the VPS account. It is still unknown whether DNS for this domain sits in that same account, in another Hostinger account, or in an account someone else controls.

3. **Whether that same login also holds `otonest.com` and `hatipara-ssh.com`.** Those zones must stay unchanged if only `symamlaw.com` is meant to move. Knowing the account boundary shows whether one login can edit the wrong zone.

4. **The Namecheap screen for this domain.** Public RDAP already shows the parking nameservers. Confirmation that the account is on Custom DNS, and that Advanced DNS refuses edits, closes the registrar side. The client can see this without sharing a password.

5. **The VPS end date in Hostinger billing.** 5 November 2026 was supplied for this audit and was not on the server. The date decides how long `88.222.215.187` remains a valid target, for this site and for the other two.

6. **Whether phpMyAdmin is still required.** It is live on HTTP. A zone change that drops or keeps `phpmyadmin` has a different effect depending on that answer.

7. **Whether anyone receives mail at `@symamlaw.com`.** The zone has no MX. Outbound mail already uses Gmail. If receiving mail matters, the current DNS does not provide it, and that is a separate fact from the website records.

8. **The SSLCommerz return and IPN URLs as stored by SSLCommerz, and whether the application sends `ipn_url`.** The code builds success, fail, and cancel from the Laravel root plus `/purchase/success`, `/purchase/fail`, and `/purchase/cancel`, and `APP_URL` is the backoffice host. The merchant panel can disagree with the code. Those URLs have to keep resolving if current payments must continue.

9. **The Google sign-in redirect hostname.** The Laravel API exposes `POST /social-login/google`. The redirect URI registered at Google was not read. A hostname change can break sign-in while DNS for the website still looks fine.

10. **Any other integration registered against these hostnames or this IP.** DNS shows no extra mail or verification TXT records. This pass did not inventory SMS providers, mobile apps, or third-party allow-lists beyond the URL settings and SSLCommerz paths above.

## Facts collected

Namecheap holds the registration until 26 February 2027 and delegates DNS to Hostinger’s `dns-parking.com` nameservers. Hostinger’s zone is what the internet uses. The apex and a wildcard point at `88.222.215.187`. There is no AAAA, MX, SPF, DMARC, or CAA record. `www`, `admin`, `backoffice`, and `phpmyadmin` resolve to that address and have Nginx vhosts. `api` resolves only because names under the zone resolve; Nginx has no `api` server, and HTTPS for an unknown name presents the admin certificate.

The zone cannot be read or exported with the SSH login. A nameserver change to Namecheap BasicDNS would use `dns1.registrar-servers.com` and `dns2.registrar-servers.com`, and Namecheap does not copy the old records. Editing the current records in place requires the Hostinger DNS account. The record list inside that account, who can open it, the VPS end date, and the payment and Google hostnames that must keep working are still unconfirmed.

No DNS provider is selected here.

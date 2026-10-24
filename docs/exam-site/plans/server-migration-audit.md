# Server migration audit

**Done.** The report is [../domain-ssl-migration-audit.md](../domain-ssl-migration-audit.md). Do not run this audit again. The "KVM 2 is not purchased" lines below are the 23 October 2026 brief. The box is bought and provisioned. The old site's Gmail SMTP stays on the old VPS. The new API uses Brevo, and those settings go on the server only when this build is the running process. That step is [auth-and-mail.md](auth-and-mail.md) section 4.

Read-only audit of the current Hostinger VPS, the Namecheap domain, public DNS, Nginx, and SSL. This plan follows Prompt 1 and [decisions.md](../decisions.md) (Server migration, 2026-10-23). It does not change a decision there.

This is not feature work. It does not implement SSLCommerz, buy the KVM 2, or edit the live server.

## Outcome

One report, `docs/exam-site/domain-ssl-migration-audit.md`, that states how the current site is served and what has to move before exam checkout can be tested. The report records the domain name as read from the server. This plan does not contain that name.

Stop after the report. Wait for Shahriar. The next phase (DNS cutover, certificate work, or anything on the new box) starts only after he says so.

## What is already decided

- The client keeps the existing Namecheap domain. The name is not in this repo. Read it from Nginx on the current VPS. Do not invent one, and do not take it from an older brief.
- That domain is served by Nginx on the current Hostinger VPS, with SSL already installed. The VPS expires on 5 November 2026. Shahriar has SSH.
- The replacement is a Hostinger KVM 2. It is not purchased. There is no new box to configure.
- The new box will host the Next.js site and the Spring Boot API. The Flutter app calls that API. Flutter screens stay out of this delivery.
- Exam checkout is SSLCommerz. Credentials stay in environment variables. The report names variable names only if it must, and never values.
- SSLCommerz needs a public HTTPS URL on this domain for the callback or IPN. The hostname, and sandbox versus live, stay open until this report and until Shahriar confirms them.
- Course and book payments, including manual bKash/Nagad, are out of this delivery. This audit does not inspect or change those payment rows.
- A September 2026 note in `docs/Client Request/exam_portal_audit_brief.md` describes a shared server (`srv633348`, other apps, no Java, no PostgreSQL). [client-brief.md](../client-brief.md) section 10 marks that deployment context outdated for the new box. Treat the note as stale. Re-read the live server. Record other hostnames only as neighbors on the current box.

## Hard stops

Do not edit, create, move, or delete files on the server. Do not install packages. Do not stop, restart, reload, or disable services. Do not change DNS, nameservers, firewall rules, application files, environment values, or databases. Do not issue, renew, revoke, replace, or delete certificates. Do not log in to Namecheap, Cloudflare, Hostinger billing, or SSLCommerz. Do not purchase the KVM 2. Do not write feature code.

Do not print private keys, passwords, tokens, store passwords, or secret environment values. Record private-key paths only. If a command would dump a secret, skip it and note the skip.

`nginx -t` and `nginx -T` parse config. They do not reload Nginx. `certbot certificates` lists certs. It does not renew them.

## Before the first SSH command

Shahriar confirms the SSH target (user and host, or an existing host alias). The repo does not store it. If the session cannot authenticate, stop and list the missing access. Do not guess a host from the September note.

Run the server commands over that one SSH session. Run public DNS lookups from the workstation after Phase 1 has named the hostnames. Do not assume every Nginx `server_name` is publicly active.

## Phase 1 — Server and website

Read-only. Report OS and version, hostname, and public IPv4/IPv6 if the box exposes them. Report Nginx version, service state, and the active config paths. List every `server_name`, and which blocks listen on port 80 and port 443.

For each site, record whether Nginx serves files itself or proxies to an app, the root or `proxy_pass` target, and the deploy directory when a config or process list shows it. Record listeners and the process manager actually in use (systemd, PM2, Supervisor, Docker, or none). Note any hostname that looks like an API or staging name, and say whether that is only a config name or also a running process.

Suggested commands, all read-only:

```bash
cat /etc/os-release
hostname -f
hostname -I
ip -4 addr; ip -6 addr
nginx -v
systemctl is-active nginx
systemctl is-enabled nginx
nginx -t
nginx -T
ss -tulpn
systemctl list-units --type=service --state=running --no-pager
ps aux
```

Run `pm2 list`, `supervisorctl status`, and `docker ps` only when those binaries exist. A missing binary is a finding, not a prompt to install it.

Also record, without changing anything: Java, PostgreSQL, Node, free memory, disk, and swap. The September note said Java and PostgreSQL were absent and swap was zero. Re-verify. Those facts size the move. They do not authorize installing anything.

## Phase 2 — Domain and DNS

Use the hostnames from Phase 1. Query public DNS for A, AAAA, CNAME, MX, NS, TXT, SOA (for TTL), and CAA. Cover the apex, `www`, any API or staging name found in Nginx, and any other name whose A/AAAA or CNAME points at this VPS.

Record authoritative nameservers and what they imply (Namecheap, Cloudflare, or another host). Compare each address to the VPS addresses from Phase 1. Note TTL from the SOA or the record when the lookup shows it. Note CDN or proxy signals when they are visible (nameserver brand, a proxy address that is not the VPS).

Separate three lists:

- Configured in Nginx.
- Published in public DNS and aimed at this VPS.
- Published in DNS and aimed somewhere else.

Email records to preserve are MX, and TXT used for SPF, DKIM, and DMARC, plus any CAA. Changing the website A/AAAA record must leave those in place.

Public DNS does not prove who can edit the zone. If the server has no registrar or DNS-panel evidence, the report asks Shahriar for the Namecheap DNS login, or for the login of whoever the nameservers belong to.

Lookups from the workstation (`Resolve-DnsName` on Windows, or `dig` if it is installed). No registrar login.

## Phase 3 — SSL/TLS

Identify how certificates are managed (Certbot/Let's Encrypt, a copied fullchain, or another client). For each cert: names, covered hostnames, issuer, not-before, not-after, fullchain path, and private-key path. Read certificate files with `openssl x509 -in <cert> -noout -subject -issuer -dates -ext subjectAltName`. Do not `cat` key files.

Map each Nginx `ssl_certificate` directive to that inventory. State whether the apex, `www`, and any API name are covered. Check chain and live HTTPS with `openssl s_client` or `curl -sI` against the public names. Record HTTP-to-HTTPS redirects, HSTS, and TLS protocol/cipher lines from the Nginx dump.

Renewal: `systemctl list-timers`, `certbot certificates`, and cron paths under `/etc/cron.*` when present. State HTTP-01, DNS-01, or unknown. Name any DNS-API credential file path without opening secrets. A webroot or standalone authenticator is HTTP-01. A `dns-*` authenticator is DNS-01.

## Phase 4 — What the report concludes

Answer these from the evidence. Label a gap instead of filling it.

1. Which records must change so the domain reaches the new KVM 2 (expected: apex and `www` A/AAAA, plus any app hostname that should move).
2. Which records stay (MX, mail TXT, CAA unless it blocks issuance, and any name that belongs to another service).
3. Whether the current certificate's files could be copied. Copying a live Let's Encrypt cert is technically possible and a poor default: issuance is tied to the account and the validation path, and the new box should get its own cert.
4. A fresh Let's Encrypt certificate on the new box is the path to recommend, after HTTP-01 or DNS-01 can succeed there.
5. HTTP-01 needs the name to resolve to the new box, or a short, deliberate validation window. DNS-01 can issue before the A record moves. The report states which method the current box uses, and which method the new box can use.
6. Whether current hostname coverage is enough for the website plus an API name. SSLCommerz's callback hostname is still Shahriar's choice. The report lists the names that already resolve and already have a certificate. It does not pick the callback host.
7. CAA records, a DNS proxy, or an HTTP-01 blocker that would stop a new certificate.
8. The new box can be tested with a hosts-file override or a temporary name before the public A record changes. The box does not exist yet, so this is a sequence, not a task for this audit.
9. Low downtime is a DNS change with TTL lowered beforehand, the new site already serving HTTPS, and the old site left running for rollback. Email records stay put, so mail does not follow the website cutover.
10. Access still required from Shahriar or the client: SSH if this pass could not log in; the DNS account if the server does not show who edits the zone; confirmation of the callback hostname; sandbox versus live SSLCommerz; whether any current site data must move. Do not invent a data migration.

Keep the four layers separate in the write-up. Registration is the Namecheap domain. DNS is the zone at the authoritative nameservers. Nginx is the server block on a box. The certificate is a file pair Nginx presents. Moving the website changes DNS and requires a certificate on the new box. It does not transfer the registration, and it does not by itself move mail.

## Phase 5 — Report

Write `docs/exam-site/domain-ssl-migration-audit.md` with these sections:

- Executive summary
- Current VPS and website architecture
- Domain and hostname inventory
- DNS record inventory and current destinations
- DNS provider and nameserver findings
- SSL certificate inventory and hostname coverage
- Certificate renewal and validation details
- Existing Nginx configuration relationships
- Email and other DNS records that must be preserved
- Migration risks and dependencies
- Exact information or access needed from the client
- Recommended migration sequence
- Rollback strategy
- Pre-migration and post-migration verification checklist

Quote config paths and short non-secret excerpts. Verified facts and assumptions go in different sentences.

Payment testing stays blocked until the KVM 2 exists, the Namecheap zone points at it, and HTTPS is up on that box. The report's migration sequence ends at "ready for Shahriar to choose the callback hostname and the credential profile." It does not store a store id or a store password.

## Rollback, stated now

The old site remains on the current VPS until that VPS expires on 5 November 2026. Rollback of a later cutover is putting the website A/AAAA records back to the current VPS address. Mail records are not part of that change. This audit does not perform that change.

## Verification of this audit

The pass is complete when the report exists, every Phase 1–3 item is answered or marked unknown, the domain string in the report was read from the server, and no server command in the session was a write. `git status` on the repo should show the report (and this plan) only.

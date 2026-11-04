# Guide: the client's domain and live SSLCommerz payments

**Status: planned, not deployed.** The steps below have not been run on the live server, except the DNS records (added in Namecheap by the owner on 2 November) and the read-only preflight. Do not start phase 2 or later without the owner's go-ahead.

2 November 2026. **Audience:** Shahriar, and the agent helping him on the box. **What it covers:** putting the exam site on `exam.symamlaw.com` (students) and `admin.symamlaw.com` (staff), then taking real payments through the client's live SSLCommerz store. Who does what, and in what order, is in [live-payment-readiness-plan.md](live-payment-readiness-plan.md).

No store id, store password, panel login or card number goes in this file, in Git, or in a chat. The live store values stay in the gitignored `docs/exam-site/config-files/payment-credentials.md` and in `/etc/coaching-api.env` on the box.

## The picture

```
Namecheap DNS (symamlaw.com)
  symamlaw.com, www, backoffice, *         ->  88.222.215.187   old server, old site   (unchanged)
  exam                                     ->  187.52.115.111   new server, exam site  (new)
  admin                                    ->  187.52.115.111   new server, exam admin (new)
  admin.exam                               ->  187.52.115.111   redirects to admin     (kept from 2 Nov)

Student browser -> https://exam.symamlaw.com -> Nginx -> website :3000, /api/ -> API :8080
SSLCommerz      -> https://exam.symamlaw.com/api/v1/live-exam-orders/{tranId}/return | /ipn -> API
API             -> https://securepay.sslcommerz.com (session, validation, transaction query)
```

The old site is not touched. Its DNS no longer answers for `admin.symamlaw.com` (checked 5 November), so the exam admin takes that name. `admin.exam.symamlaw.com`, the 2 November name, stays as a redirect to it. If the client picks other names, replace them everywhere below, in `deploy/nginx/` and in the preflight's `SITE` and `STAFF`.

## How the API handles a payment

- `POST /api/v1/live-exam-orders` creates the order and asks SSLCommerz for a session. It sends four URLs built from `SSLCZ_CALLBACK_BASE_URL`, each carrying the order's tran id:
  - success, fail, cancel: `https://exam.symamlaw.com/api/v1/live-exam-orders/{tranId}/return?status=…`
  - IPN: `https://exam.symamlaw.com/api/v1/live-exam-orders/{tranId}/ipn`
- Access is granted only when SSLCommerz's validation API says `VALID` or `VALIDATED` and the tran id, amount, currency (BDT) and store id match the order. The IPN normally does this before the student is back. The browser return does the same check. A job every 10 minutes asks SSLCommerz about orders that are still open, in case both were missed.
- After the return, the browser goes to the first `WEB_ORIGIN` plus `/purchase/result`.
- `SSLCZ_BASE_URL` decides sandbox or live, and the profile must agree: `staging` starts only with `sandbox.sslcommerz.com`, `prod` only with `securepay.sslcommerz.com`. Any other pairing stops the API at startup. So **a live payment needs the `prod` profile.**

## Phase 0. Release the current code

The box runs the release of 30 October (database at Flyway 55). The branch is ahead (migrations up to V64, the repricing fix of 2 November). Release it on today's hosts first, so the host move and the live switch are not mixed with a code change.

The release script now requires the hosts. For the current staging hosts, pass them explicitly: `SITE_URL=https://srv2089893.hstgr.cloud STAFF_URL=https://admin.187-52-115-111.nip.io bash web/scripts/release-build.sh <out.tgz>`.

Use the usual procedure from [vps-staging-deploy.md](vps-staging-deploy.md) and [../reports/client-review-deploy.md](../reports/client-review-deploy.md): database dump first, previous jar and website kept, the jar installed as `deploy:deploy 640`, the website built with `bash web/scripts/release-build.sh <out.tgz>` under Git Bash and swapped in with `web/scripts/deploy-web.ps1`. Record it in `client-review-deploy.md`.

Check: the preflight with today's hosts passes.

```powershell
Get-Content -Raw docs/exam-site/deploy/live-payment-preflight.sh |
  ssh exam-kvm "tr -d '\r' | sed '1s/^\xEF\xBB\xBF//' | MODE=sandbox SITE=srv2089893.hstgr.cloud STAFF=admin.187-52-115-111.nip.io bash -s"
```

Use Windows OpenSSH from PowerShell; Git Bash `ssh` is refused by the box.

## Phase 1. DNS in Namecheap

1. Namecheap → Domain List → `symamlaw.com` → Manage → **Advanced DNS**.
2. Add two records. Leave every existing record alone.

| Type | Host | Value | TTL |
|---|---|---|---|
| A Record | `exam` | `187.52.115.111` | Automatic |
| A Record | `admin` | `187.52.115.111` | Automatic |

The `admin.exam` record added on 2 November stays as it is, for the redirect.

3. Check from the PC until both return the new address:

```powershell
Resolve-DnsName exam.symamlaw.com -Type A -Server 8.8.8.8
Resolve-DnsName admin.symamlaw.com -Type A -Server 8.8.8.8
```

Both names answered `88.222.215.187` before this, through the wildcard. Once the `exam` record exists, the wildcard no longer covers anything under `exam`. `admin` has no record of its own until the row above is added, so it answers "name does not exist" until then. A resolver can keep the old wildcard answer until its cached TTL ends (up to 4 hours), so if a resolver disagrees, ask Namecheap directly: `Resolve-DnsName exam.symamlaw.com -Type A -Server dns1.registrar-servers.com`.

## Phase 2. Nginx and certificates

The box already has Certbot 2.9 with its renewal timer, and ufw allows ports 80 and 443. The two site files are in `docs/exam-site/deploy/nginx/`.

1. Copy both files to `/etc/nginx/sites-available/` on the box, under the same names, and link them:

```bash
sudo ln -s /etc/nginx/sites-available/exam.symamlaw.com /etc/nginx/sites-enabled/
sudo ln -s /etc/nginx/sites-available/admin.symamlaw.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

2. Issue one certificate per host. Certbot edits each file to add HTTPS and the port 80 redirect. Give an email so expiry warnings reach someone.

```bash
sudo certbot --nginx -d exam.symamlaw.com -m <owner email> --agree-tos --redirect
sudo certbot --nginx -d admin.symamlaw.com -d admin.exam.symamlaw.com -m <owner email> --agree-tos --redirect
sudo certbot renew --dry-run
```

3. Check: `https://exam.symamlaw.com` and `https://admin.symamlaw.com` load the site with a valid padlock. The app still thinks it lives on the old hosts until phase 3, so a sign-in there may redirect to the old host. That is expected.

If Certbot fails: the name does not resolve to the box yet (phase 1), or port 80 is closed by a firewall in Hostinger's hPanel.

The old hosts keep their sites and certificates. Do not delete them; they are the way back.

## Phase 3. Move the app to the new hosts

Four places hold a host name. Change them together, in this order, in one sitting.

1. **Web service unit** (read at request time, and checked by the website deploy). Back it up, then set:

```
Environment=SITE_URL=https://exam.symamlaw.com
Environment=STAFF_URL=https://admin.symamlaw.com
```

in `/etc/systemd/system/coaching-web.service`, then `sudo systemctl daemon-reload` (no restart yet).

2. **API env file** (Shahriar edits it; copy it to `coaching-api.env.before-hosts` first):

| Name | New value |
|---|---|
| `WEB_ORIGIN` | `https://exam.symamlaw.com,https://admin.symamlaw.com` (student first: the payment return and mail links use the first, staff mail links the second) |
| `SSLCZ_CALLBACK_BASE_URL` | `https://exam.symamlaw.com` (no path, no trailing slash) |

Then `sudo systemctl restart coaching-api` and watch `journalctl -u coaching-api -f` until it has started.

3. **Website build** (the host names are baked into the browser bundle). Under Git Bash on the PC, from a clean commit:

```bash
SITE_URL=https://exam.symamlaw.com STAFF_URL=https://admin.symamlaw.com bash web/scripts/release-build.sh /tmp/web-hosts.tgz
```

The script keeps the API base empty, which must stay so (an absolute API base logged the admin out on every refresh on 29 October).

4. **Deploy it** with `web/scripts/deploy-web.ps1`. The box-side check compares the bundle with the unit's `SITE_URL` and `STAFF_URL` from step 1 and refuses a mismatch.

Check: preflight with the defaults passes (`MODE=sandbox`). Sign in as a student on `exam.symamlaw.com` and as staff on `admin.symamlaw.com`; reload; both stay signed in.

## Phase 4. One sandbox payment on the new hosts

Still `staging` and the sandbox store. This proves the new callback host and the IPN before any real money moves.

1. Sign in as a student on `https://exam.symamlaw.com`, buy a priced exam, and pay on the sandbox page with the sandbox card or the sandbox bKash option. Never a real card.
2. On the box:

```bash
journalctl -u coaching-api --since '-15 min' --no-pager | grep -E 'sslcommerz|live exam payment|/ipn'
```

Pass: `sslcommerz session accepted`, `sslcommerz validation accepted`, `live exam payment granted`, and a `POST …/ipn` line with status 200. The result page says paid and the exam opens. A grant from the browser return alone does not prove the IPN.

## Phase 5. Clear the review data

Only on Shahriar's explicit go. Real money must not land in a database that still has demo accounts with a shared password (including a System Admin).

1. Dump the database first (`/var/backups/coaching/`, copy to the PC).
2. Run `api/.cursor/progress/ui-delivery/demo-cleanup.sql` in one transaction. It deletes exactly the seed's fixed rows, including the four `DEMO…` orders.
3. Remove the test student made for the mail checks (`01711000099`) from the Students page, if it is still there.
4. In the env file, delete `MASTER_ADMIN_PASSWORD` and `MAIL_TEST_ENABLED`. Confirm no `SEED_*` line says `true`.

The Master Admin account stays. Check that the Master Admin can still sign in on the admin host.

## Phase 6. The live store at SSLCommerz

The store is the client's. According to `reports/sslcommerz-status.md`, the old site's env holds the live store as its active pair, so the old site probably takes course and book payments through it today. Any panel change can break those. Find this out before changing anything.

What to settle with SSLCommerz (the client asks, from the merchant account):

1. The store is live and bKash, Nagad and cards are on.
2. Whether the live store accepts callback URLs on `exam.symamlaw.com` sent with each payment, or needs that domain registered first.
3. That no fixed IPN URL in the panel overrides the per-payment `ipn_url`. The API sends a different IPN URL for each order, so a single panel URL cannot work for it. If the old site relies on a panel IPN URL, ask whether the per-payment URL still wins.
4. How a refund is made from the panel, and how long it takes.

A message the client can send:

> We are adding a second website on our domain, exam.symamlaw.com, that will take payments through our existing store [store name]. Each payment request will send its own success, fail, cancel and IPN URLs under https://exam.symamlaw.com/api/v1/live-exam-orders/. Please confirm (1) that our live store accepts these URLs or tell us what we must register, (2) that a fixed IPN URL in our panel will not override the per-payment ipn_url, and (3) that our existing website's payments are not affected. bKash, Nagad and cards should stay enabled.

Make only the panel changes SSLCommerz asks for. Note what was changed and when.

## Phase 7. Switch to live

Needs phases 4, 5 and 6 done.

1. Copy the env file to `coaching-api.env.sandbox` (the way back).
2. Shahriar edits `/etc/coaching-api.env`:

| Name | Live value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SSLCZ_BASE_URL` | `https://securepay.sslcommerz.com` |
| `SSLCZ_STORE_ID` | the live store id |
| `SSLCZ_STORE_PASSWORD` | the live store password |
| `SSLCZ_CALLBACK_BASE_URL` | `https://exam.symamlaw.com` (unchanged) |

3. `sudo systemctl restart coaching-api` and watch the log. If it stops with an `IllegalStateException`, read the message: it names the wrong pairing or a missing value. `prod` also turns off Swagger and refuses any `SEED_*=true`.
4. Preflight in live mode:

```powershell
Get-Content -Raw docs/exam-site/deploy/live-payment-preflight.sh |
  ssh exam-kvm "tr -d '\r' | sed '1s/^\xEF\xBB\xBF//' | MODE=live bash -s"
```

Every line must say PASS before the test.

## Phase 8. The live payment test

Use a small amount and an account the team controls. Do it before the site is announced, so no real student finds the test exam.

1. As Master Admin, publish one exam priced **10 BDT** (a single exam, so nothing else is unlocked).
2. **Wrong OTP.** As the test student, buy it and pay by card or bKash, typing a wrong OTP. Expect: the result page shows failed, the order is not paid, no grant, no money taken.
3. **Good payment.** Buy again and pay correctly. Expect, all of these:
   - log: `sslcommerz session accepted`, `sslcommerz validation accepted`, `live exam payment granted`, `POST …/ipn` status 200
   - result page: paid; the exam opens for the student
   - the receipt email reaches the student, the purchase email reaches the Master Admin
   - the admin Payments page shows the order as paid with its channel
   - the transaction appears in the SSLCommerz merchant panel with the same tran id and 10.00 BDT
4. If time allows, repeat step 3 with the other channel (card or bKash).
5. **Refund** the good payment(s) from the merchant panel. The API has no refund, so the order stays paid in the app; note the tran id in the deploy record.
6. Unpublish or archive the 10 BDT exam.
7. Record the run in `reports/client-review-deploy.md` (tran ids are fine to write; no card data, no `val_id`).

If the IPN line is missing but the order became paid through the browser return, the panel is probably overriding the per-payment IPN URL. Ask SSLCommerz to clear it before real sales; until then the 10-minute job still catches a payment the return missed.

## Rollback

| From | Back to | How |
|---|---|---|
| Live (phase 7) | Sandbox | Copy `coaching-api.env.sandbox` back, restart the API |
| New hosts (phase 3) | Old hosts | Copy `coaching-api.env.before-hosts` back, restore the web unit backup, `daemon-reload`, put the previous website release back (`/opt/coaching-web.before-…`), restart both services |
| Nginx sites (phase 2) | Without them | Remove the two links from `sites-enabled`, `nginx -t`, reload. Certificates can stay |
| DNS (phase 1) | Wildcard answer | Delete the `exam` and `admin` A records in Namecheap (`admin.exam` stays) |
| Data (phase 5) | Review data | Restore the phase 5 dump (`pg_restore --clean`), only with Shahriar's OK |
| Release (phase 0) | 30 October release | The kept jar, website release and dump, as in the deploy record |

## Things to know

- A payment SSLCommerz marks risky (`risk_level` 1) is granted, and shows a "Risky" pill on the staff payments list and a warning on its detail. The Master Admin checks it in the merchant panel within a day and refunds it there if it is fraud.
- An order already failed or cancelled that is paid afterwards still grants on an IPN or browser return. If both are lost, neither the recheck job nor the Master Admin's confirm picks it up. Find the payment in the merchant panel and give access with a manual grant. No receipt is sent.
- A student who clicks Buy again after a price change gets a new order at the new price, and the old one is cancelled (2 November change). If they still finish the old SSLCommerz page, that payment is granted at the old amount.
- The recheck job's give-up counter lives in memory, so a restart lets it ask again. That is harmless.
- `www.symamlaw.com` has a certificate problem on the old server (audit of 23 October). It does not affect the exam hosts.

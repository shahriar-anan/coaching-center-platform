# Client review deploy, 27 October 2026

Record of putting branch `demo/client-review` on the staging VPS for the client. Plan: [../plans/client-review-staging.md](../plans/client-review-staging.md). No secret values are written here.

## What went out

| | |
|---|---|
| Commit | `3796330` on `demo/client-review` (clean tree at build) |
| Jar | `api-0.0.1-SNAPSHOT.jar`, SHA-256 `64fb0935461f5d55892d705d46f94bca5e99b0d27843a00f1f562d738c03aa60`, installed as `/opt/coaching-api/api.jar` (`deploy:deploy 640`) |
| Flyway | 38 to 44 (V39–V44 applied on start), all successful |
| Website | Next.js standalone build, Linux `sharp` swapped in, in `/opt/coaching-web`; previous folder kept as `/opt/coaching-web.old` |
| Student host | `https://srv2089893.hstgr.cloud` |
| Admin host | `https://admin.187-52-115-111.nip.io` (Let's Encrypt, valid to 2027-01-25, renewed by `certbot.timer`; registered without an email address, so no expiry mail) |
| Seed | ran once on the first start of this jar: 6 categories, 4 packages, 19 exams (all 20 minutes), 6 sittings, 4 orders, 3 grants, 5 accounts |

## Tests before the build

- Web: lint 0 errors; 112 files, 672 tests pass.
- API: 392 tests; 390 pass. `BookOrderIT` and `CoursePurchaseIT` fail with 409 on create. Decision V1 of the VPS deploy plan names both as known (course and book payments are outside this delivery).
- One test (`AuthorizationTest`) was updated to the newer rule that a System Admin can suspend and restore students (admin-ui plan item 9).

## What changed on the box

- `/etc/coaching-api.env` (written by Shahriar): `WEB_ORIGIN` lists both hosts, student host first; `SEED_DEMO` and `SEED_DEMO_PASSWORD` added; `SEED_ADMIN_*`, `SEED_STUDENT_*` and `SEED_ENABLED` removed. Copies: `.prev` (25 October) and `.before-review`.
- `coaching-web.service`: `SITE_URL` and `STAFF_URL` added (read by `proxy.ts` at request time). Copy: `.prev`.
- Nginx: new site `coaching-admin`; `snippets/coaching-review.conf`; `conf.d/coaching-review-gate.conf` (gate secret, `map_hash_bucket_size 128`); `/etc/nginx/coaching-review.htpasswd` (user `review`). Copies: `coaching.prev`, `*.pre-auth`, `/root/coaching-review-gate.conf.prev`.
- Package `apache2-utils`.
- Backups: `/var/backups/coaching-pre-review-20261027-0823.dump` (SHA-256 `c30eb03e…652d`, also on the PC outside the repo), `api.jar.prev`.

## Access control

Removed on 27 October 2026 at Shahriar's request. Both hosts are open to anyone who has the address, and the demo accounts (including the System Admin and the Instructor, password from `SEED_DEMO_PASSWORD`) can be signed in to by anyone who guesses their phone or email. For a short review that is Shahriar's call; before the 29 October cutover the demo accounts go with `demo-cleanup.sql`.

For the record, until then the hosts had Nginx basic auth on the pages plus a gate cookie on `/api/`, with the SSLCommerz callbacks open. The files are kept in `/root/review-auth-removed/` on the box (`*.with-auth` sites, the snippet, the gate file and the htpasswd file), and `sites-available/*.pre-auth` hold the sites as they were before. To turn it back on: copy the `.with-auth` sites back, the snippet to `/etc/nginx/snippets/`, the gate file to `/etc/nginx/conf.d/`, the htpasswd file to `/etc/nginx/`, then `nginx -t` and reload.

## Checked on the live hosts

| Check | Result |
|---|---|
| Pages and `/api/` after basic auth was removed | 200 on both hosts; `/api/v1/me` 401 without a sign-in |
| Student host `/staff`; admin host `/`, `/register`, `/mcq` | redirected to the other host / to `/staff` as designed |
| Sign-in, all five accounts, by phone and email | roles SYSTEM_ADMIN, INSTRUCTOR, STUDENT x3 |
| Instructor | sees the written and mixed packages; refused on the MCQ package |
| System Admin payments | 3 paid, 1 failed |
| Student A | 4 unread notices |
| Public catalogue (signed out) | new categories and packages |

## Website update, 27 October 2026 (second release)

Web only; the jar and the database are unchanged. Commit `ded922e` plus the docs commit after it (last code change `ded922e`). Web build with the same two host URLs, Linux `sharp` swapped in, uploaded and swapped into `/opt/coaching-web` (the release before it is `/opt/coaching-web.old`).

| Change | Live check |
|---|---|
| Landing menu: `/publications`, `/blog`, `/media`, `/resources` each show a "coming soon" page, and the menu links point to them (they all went to `/help`) | all four return 200 with the text; the landing page links to all four |
| Admin sign-in page on the admin host: its own frame, heading, role chips, no sign-up link, no Google button, a line that the Master Admin password cannot be reset here, kept out of search engines | admin `/login` has the admin heading and no `/register` link; the student `/login` is unchanged |
| Real browser sign-in on the admin host as the demo staff account | landed on `/staff`, dashboard showed 4 papers awaiting marking and the upcoming 11 PM exams |

Pre-release checks: typecheck clean, lint 0 errors, 114 test files and 686 tests pass. Screenshots: `admin-login/live-admin-desktop-login.png` and `live-admin-desktop-after-signin.png`.

Rollback for this release: stop `coaching-web`, put `/opt/coaching-web.old` back as `/opt/coaching-web`, start it.

## API update, 27 October 2026 (third release): the admin's cases

API only; no schema change, no env change. Commit `fb18540`, jar SHA-256 `f9882d8a6364d3f4f133710a808262bb48f779699cf66cbab1ff0a017bccd8f0`. Backups first: `/var/backups/coaching-pre-topup-20261027-0936.dump` (also on the PC), and the running jar kept as `api.jar.before-topup`. The API started in about 21 seconds, and the seed's top-up ran on that start (`SEED_DEMO` was still `true`). It adds data only; nothing the client had done was touched.

| Added | Live check |
|---|---|
| Two draft packages and four draft exams waiting for the Master Admin to publish | database lists them; the Participation page has a drafts tab with 4 rows (two ready, two needing work) |
| Two written papers marked but not released, one flagged ready | dashboard "awaiting publish" 2; the four papers awaiting a mark unchanged |
| Eleven orders: 5 paid, 2 failed, 1 cancelled, 3 pending (older than a day, so the recheck job leaves them) | dashboard "pending payments" 3; the pending payments page lists the three; the orders API returns all eleven by state |

Tests before the release: the seed suite (17) passes, including a test that applies the top-up to a database seeded before it and keeps a mark changed in between. Full API suite: 396 tests, 394 pass; `BookOrderIT` and `CoursePurchaseIT` fail with 409 as before (accepted, V1 of the VPS deploy plan).

Rollback: stop `coaching-api`, copy `/opt/coaching-api/api.jar.before-topup` over `/opt/coaching-api/api.jar`, start it. The top-up rows stay in the database; they harm nothing, and `demo-cleanup.sql` removes them.

## Mail and watermark release, 27 October 2026 (fourth release)

API and website. Commit `9255412`; jar SHA-256 `f059425c5936b85558a9cd9c9225ca69d4d88825b4bcbf2866d1c549ed3af17a`. No schema change, no env change. Backups first: `/var/backups/coaching-pre-mail-20261027-*.dump` (also on the PC), the previous jar as `api.jar.before-mail`, and the previous website as `/opt/coaching-web.before-mail`. The API started in about 21 seconds.

| Change | Live check |
|---|---|
| Every mail uses one branded template: the SYMAM LAW mark as a linked header image (nothing attached), Bangla text with an English twin, a plain-text part, a footer. A new account's mail says "Welcome on board" and greets by first name. Staff are linked to the admin site. | brand images answer 200 (`/brand/logo-light.png`, `/brand/copy-icon.png`); no send failures in the log; a welcome email was sent to the owner's inbox as a real test |
| A code is shown in a box that selects with one tap, with a copy button and icon that opens a new `/code` page whose button really copies. The code rides in the address fragment, never a query; the activate and reset pages fill it in from there. | `/code` answers 200 on the student site and redirects from the admin site |
| Fixed watermark words (Sample, Staff view, "for marking only") now read SYMAMLAW in both languages. A student's own sitting still draws name and student code. | pinned by a test |

A test student `Shahriar Anan` (phone `01711000099`, the owner's email) was created through the admin API to send the real email. It is a demo-style account with a random password nobody knows; it is set by using the activation or reset email. Remove it from the Students page (Master Admin) when the test is finished.

Tests before the release: web 116 files and 700 tests pass, lint 0 errors; API 406 tests, 404 pass (`BookOrderIT` and `CoursePurchaseIT` fail with 409 as before, accepted).

Rollback: stop `coaching-api`, copy `api.jar.before-mail` over `api.jar`, start it; stop `coaching-web`, put `/opt/coaching-web.before-mail` back as `/opt/coaching-web`, start it.

## Payment receipt release, 27 October 2026 (fifth release)

API only. Commit `cc707af`; jar SHA-256 `f580991114275533de949dc4977677f4d000e3b1d0b8562d9053102d3fe2c242`. No schema, env or website change. Backups first: `/var/backups/coaching-pre-receipt-20261027-*.dump` (also on the PC) and the previous jar as `api.jar.before-receipt`. The API started in about 21 seconds.

The receipt a buyer gets for a paid package or exam is now minimal: the SYMAM LAW header, a thank-you by first name, a "Paid" mark with the amount in large type, and four details (the package or exam name in Bangla and English, how it was paid, the transaction number, the Dhaka time), plus a button that opens the exam, or the buyer's list for a package. The text version carries the same facts.

Checked live by sending two real receipts to the owner's inbox through the app's own mail job: one for a package (BJS Preliminary MCQ Package, 1500.00, bKash) and one for a single exam (Evidence MCQ, 250.00, Nagad). Both rows went from PENDING to SENT, with no send failures in the log. The two purchases were written directly as a paid order, its access grant and a queued receipt row (confirming a payment through the panel needs the Master Admin's login), with the transaction ids `DEMO-MAIL-PKG-1` and `DEMO-MAIL-EXM-1`, so `demo-cleanup.sql` removes them. No purchase note was queued, so nothing went to the Master Admin. The test student (`01711000099`) was made active through the admin API; it still has no password.

Tests before the release: web 116 files and 700 tests pass; API 409 tests, 407 pass (`BookOrderIT` and `CoursePurchaseIT` fail with 409 as before, accepted).

Rollback: stop `coaching-api`, copy `api.jar.before-receipt` over `api.jar`, start it.

## Website fix, 30 October 2026: admin logged out on every refresh

Web only; same commit as the live site (`8713cfc`), rebuilt with the right env. No API, database, env or Nginx change.

The cause: the two website releases of 29 October (17:12 and 17:36 UTC, builds `5B92vXoiWFLLSJqx8o1S9` and `lHFX65ZOIlGjVK_Cso__c`) were built with `NEXT_PUBLIC_API_BASE_URL=https://srv2089893.hstgr.cloud`, following the old slice 0 step 4 of the VPS plan, and without the two host URLs. Every release before them has an empty API base. With the student host baked in, the admin host called the API cross-site, so the browser never kept the `SameSite=Strict` refresh cookie. Sign-in worked (the access token is in the response body), but a page reload, or the first call after the 15-minute access token ran out, found no cookie and sent the admin to the login page. The student host was not affected, because there the API is same-origin. The missing host URLs also left the browser bundle unaware of the split hosts.

The fix: rebuild with `NEXT_PUBLIC_API_BASE_URL=` (empty), `NEXT_PUBLIC_SITE_URL=https://srv2089893.hstgr.cloud`, `NEXT_PUBLIC_STAFF_URL=https://admin.187-52-115-111.nip.io`, `NEXT_PUBLIC_GOOGLE_ENABLED=false`. Build `K5cLY7hp8U6vfkWkZ8rij`, package SHA-256 `f5581019a2e861a43b47ee7d59a93745c93502a586b0ca7bc34ec964973bebbf`. Linux `sharp` 0.35.4 binaries copied from the running release (same version), Windows binary removed. Swapped in at 18:56 UTC; the previous release is `/opt/coaching-web.before-apibase-20261029-185640`. The VPS plan's step 4 now says empty.

| Check | Result |
|---|---|
| Staged release before the swap | API base `""`, both host URLs baked in, no `localhost` in client chunks, only Linux native modules |
| After restart | up in about 1 s; admin `/login` and student `/` 200 |
| Live admin bundle over HTTPS | API base `""`; no chunk names the student host as the API |
| `POST /api/v1/auth/refresh` on the admin host | reaches the API (401 `INVALID_REFRESH_TOKEN` without a cookie) |

Tests: none run for this release; the code is the commit that was already live, only the build env changed.

Rollback: stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-apibase-20261029-185640` back as `/opt/coaching-web`, start it (this brings the bug back).

## Website update, 30 October 2026: one token refresh at a time

Web only. Commit `98339d7` (code change `625a83f`), same build env as the fix above. Requests that fail with 401 together now share one refresh call, and a 401 for a token another request already replaced only retries, so the rotated refresh cookie is never presented twice (the API ends the session when it is). Build `mKW1dHZUm-U4-WJcFIQUp`, package SHA-256 `e3b1c9ad9231c0fb846b28fa3c4fe80285faa1b01dfc9cebf3247ebe4b4797e6`, Linux `sharp` copied from the running release. Swapped in at 19:11 UTC; previous release `/opt/coaching-web.before-refresh-20261029-191125`.

Checks: staged API base `""` and only Linux native modules; up in about 1 s, admin `/login` and student `/` 200; live admin bundle over HTTPS has API base `""` and the shared refresh; `/api/v1/auth/refresh` on the admin host reaches the API.

Checked in a real browser after the release (confirmed by Shahriar, 30 October): signed in on the admin host, the admin stays signed in after a page reload and past the 15-minute access token.

Tests before the release: typecheck clean; 159 files and 1048 tests pass, including `tests/ws0/token-refresh.test.ts` (4 of its 5 cases fail on the previous client). Lint: 3 errors, all in files this change does not touch (`staff/enrolments/page.tsx`, `student-shell.tsx`).

Rollback: stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-refresh-20261029-191125` back, start it.

## Not checked

- A real browser session on either host (layout, redirects after sign-in, the basic-auth prompt in each browser).
- A sandbox payment through SSLCommerz with an IPN (deploy slice 8). It needs a browser: sign in as Student C, buy the MCQ package, pay with a sandbox test card, and look for the IPN line in `journalctl -u coaching-api`.
- The 11 PM sitting itself (open 23:00 to 23:20 Dhaka).
- Off-server backup (deploy slice 9) is still deferred.

## Left to do

- Set `SEED_DEMO=false` in the env file and restart the API (the seed is a no-op on later starts, but the switch should not stay on).
- Before the 29 October cutover: run `demo-cleanup.sql`, confirm `SEED_DEMO` is gone, and remove the review hosts or the basic auth.
- The seven nightly exams run out after 3 November. A longer review needs the cleanup and the seed again, which also resets the review's own marks and purchases.

## Rollback

| Layer | Undo |
|---|---|
| Jar | `api.jar.prev` plus `coaching-api.env.prev`, then restart. If the old jar refuses the V44 schema, restore the dump (`pg_restore --clean -d coaching`) after Shahriar's OK. |
| Website | stop `coaching-web`, swap `/opt/coaching-web.old` back, restore `coaching-web.service.prev`, `daemon-reload`, start. |
| Nginx | restore `coaching.prev` and remove `sites-enabled/coaching-admin`, the snippet, the gate file and the htpasswd file; `nginx -t`; reload. |
| Seed data | `demo-cleanup.sql` in one transaction. |

## API and website update, 30 October 2026: password change and logout

Commits `d491ef3` (API) and `58e6d2d` (web). No schema change, no env change.

- **API** jar SHA-256 `7f7c3f14a5707264dd8601900a2b7f1a28a5e026306ae7c250f0889cf6d18605`, installed at 10:11 UTC. Previous jar kept as `/opt/coaching-api/api.jar.before-pwsessions-20261030-101111`. Started in about 24 s; refresh without a cookie returns 401; no errors in the startup log. Changes: a password change ends the account's other sessions (keeps the one that made the change); the change is audited as `PASSWORD_CHANGED`; lockout counts the visitor address Nginx reports.
- **Website** build `Z7uc2Z9bJrQUU8myR1zCy`, package SHA-256 `cd9d40a5e2e07c5396259a523c4af9b4d4660a696220e62dc2dc4f65ea8343f7`, swapped in at 10:13 UTC; previous release `/opt/coaching-web.before-20261030-101312`. Changes: one password form for students and staff with Old password, New password, Confirm new password; logout lands on home and the next login does too.
- **Master Admin password** set on the server from `MASTER_ADMIN_PASSWORD` in the env file (backup `/var/backups/coaching/coaching-pre-mapw-20261030-095439.dump`). Sign-in through the API checked: HTTP 200.

Tests before the release: API full suite 664 tests, 0 failures (including `BookOrderIT` and `CoursePurchaseIT`, which passed in this run); web 160 files and 1053 tests pass; lint 0 errors in the changed files (3 errors remain in `staff/enrolments/page.tsx` and `student-shell.tsx`, untouched).

Not yet checked in a browser on the live hosts: the confirm field and the logout landing.

## Website fix, 30 October 2026: staff logout

Commit `58668e0`. Web only; API unchanged. Build `ZrRKZ2d9tiT6_I-cIKWyW`, package SHA-256 `5675c85007847363682f9aa155cc9fade1cda7e01bca4ae7f77f4e0b46543e92`, swapped in at 10:22 UTC; previous release `/opt/coaching-web.before-20261030-102213`.

Fix: after a staff logout the admin goes to the admin login instead of `/`, which redirected back to `/staff` and left the page on a loading screen until a reload. Students still go home.

Checks: web typecheck, lint and suite pass (160 files, 1054 tests). Not yet checked in a browser: staff logout from the dashboard.

## Student site moved to symamlaw.com, 4 November 2026 (Dhaka)

Web rebuild of commit `38f258e`, which was already live; only the student host changed. Build `PJKFPQ6G8l7GZSEnMMMtc`, package SHA-256 `651c4f5545d07be8d1cfbe71cd04af983850d5c6b48e2171095e405c46e471d7`, swapped in at 18:45 UTC on 3 November; previous release `/opt/coaching-web.before-20261103-184542`. API jar and database unchanged.

- **DNS (Namecheap, by Shahriar):** `@` and `www` A records to `187.52.115.111`; the `*` wildcard to the expired old VPS (`88.222.215.187`) deleted. `exam`, `admin.exam`, the Brevo DKIM, DMARC and verification records and the mail forwarding kept.
- **Google (by Shahriar):** the client's only redirect URI is `https://symamlaw.com/api/v1/auth/google/callback`. The localhost URI is gone, so Google sign-in in development needs it added back.
- **Nginx:** `sites-available/symamlaw.com` from [deploy/nginx/symamlaw.com](../deploy/nginx/symamlaw.com); `www` returns 308 to `symamlaw.com`. Certificate `symamlaw.com` covers both names, expires 1 February 2027. `exam.symamlaw.com` now returns 308 to `symamlaw.com` and keeps its certificate (previous file `exam.symamlaw.com.before-apex-20261103-184141`).
- **Env:** `WEB_ORIGIN=https://symamlaw.com,https://admin.exam.symamlaw.com`, `SSLCZ_CALLBACK_BASE_URL=https://symamlaw.com`, `GOOGLE_REDIRECT_URI=https://symamlaw.com/api/v1/auth/google/callback`. No other key changed. Unit `SITE_URL=https://symamlaw.com`; `STAFF_URL` unchanged. Backups: `/etc/coaching-api.env.before-apex-20261103-184141`, `coaching-web.service.before-apex-20261103-184141`, `/var/backups/coaching/nginx-letsencrypt-before-apex-20261103-184141.tgz`.

Checks from the box: `https://symamlaw.com/` and `/login` 200; HTTP and `www` redirect to `https://symamlaw.com`; `/staff` on the student host goes to `admin.exam.symamlaw.com`, and `/register` on the admin host comes back; CORS allows `https://symamlaw.com`; the Google start redirect names the new callback. The API restarted cleanly (26 s).

Not checked: a Google sign-in and a password sign-in in a real browser on the new host, and a sandbox payment return. Everyone signs in again once, because cookies belonged to `exam.symamlaw.com`.

Rollback: restore the env and unit backups, `daemon-reload`, put `/opt/coaching-web.before-20261103-184542` back, restore `exam.symamlaw.com.before-apex-20261103-184141`, `nginx -t`, reload, restart both services.

## Website update, 4 November 2026 (Dhaka): tab icon, hero line removed

Commit `29a1002`. Web only. Build `Hs49y_g8GKzNxnpAxlPim`, package SHA-256 `3bc69f36a17f9715cb2264eab5487abe52e6ac70afb02afd06267822b47138f4`, swapped in at 18:55 UTC on 3 November; previous release `/opt/coaching-web.before-20261103-185504`. The tab icon is the logo's scales-and-book mark; the 765+ registered-students line under the hero's sign-up button is gone. Checked from the box: the page links the new favicon, icon and apple icon, and the home page no longer contains 765. Tests not run.

## Unfinished: old-site links and Google Search (deferred by Shahriar, 4 November 2026)

Google and old links still point at the old site's paths, which 404 on the new site. Fix later with permanent redirects to the existing pages, not new pages.

| Old path | Status now | New page |
|---|---|---|
| `/about-us` (confirmed in the Internet Archive) | 404 | `/about` |
| `/registration`, `/our-packages`, `/notice`, `/contact-us` (guessed, to confirm with the links from Google) | 404 | `/register`, `/packages`, `/notices`, `/contact` |

Also open:
- `https://symamlaw.com/courses` returns 200. It belongs to the unlinked legacy admin group; check what a visitor sees and close it if exposed.
- Google Search still shows the old site's pages and icon. After the redirects, add the site to Google Search Console and submit a sitemap.
- `certbot renew --dry-run` fails because the box cannot reach Let's Encrypt's staging server (production is reachable). Re-run before the first renewal in early January 2027.

## Admin host moved to admin.symamlaw.com, 5 November 2026 (Dhaka)

Decision in `decisions.md`, "Admin host (decided 2026-11-05)". Web rebuild of commit `18ec9ec`; its only code-side difference from the live `29a1002` is the staff host (the commit changes docs and the env template). Build `rD7DwEMb6xHVcx5Cu7aMm`, package SHA-256 `c8379ce0c25beba5f2622504eacc8e077e44c2e659467e7c408058df9e5cbad4`, swapped in at 20:46 UTC on 4 November; previous release `/opt/coaching-web.before-20261104-204648`. API jar and database unchanged; the API was restarted for the env change (26 s, no open sittings).

- **DNS (Namecheap, by Shahriar):** A record `admin` to `187.52.115.111`. `admin.exam` kept. Note: the PC's network served a cached "name does not exist" for this record long after Namecheap had it; check DNS from the box (`dig @1.1.1.1`), not from the PC.
- **Nginx:** new `sites-available/admin.symamlaw.com` (the API and website proxy, as before) and a Certbot certificate for `admin.symamlaw.com` (separate certificate, `--redirect`). `admin.exam.symamlaw.com` keeps its file and certificate, but both its blocks now `return 308 https://admin.symamlaw.com$request_uri`. The repo's `deploy/nginx/admin.symamlaw.com` puts both names in one file and one certificate, for a fresh install; the box keeps them apart so no certificate was reissued.
- **Env:** `WEB_ORIGIN=https://symamlaw.com,https://admin.symamlaw.com`. No other key changed. Unit `STAFF_URL=https://admin.symamlaw.com`.
- **Backups (stamp `20261104-202419`):** `/var/backups/coaching/nginx-letsencrypt-before-admin-host-20261104-202419.tgz`, `/etc/systemd/system/coaching-web.service.before-admin-host-20261104-202419`, `/etc/coaching-api.env.before-admin-host-20261104-202419`, and `/var/backups/coaching/admin.exam.symamlaw.com.before-redirect-*`.

Checks from the box: `https://admin.symamlaw.com/login` 200, `/` goes to `/staff`, `/register` goes to `https://symamlaw.com/register`; `https://symamlaw.com/staff` goes to `https://admin.symamlaw.com/staff`; HTTP and HTTPS on `admin.exam` 308 to the same path on the new host; the CORS preflight from `https://admin.symamlaw.com` is allowed; the client chunks name `admin.symamlaw.com` and not `admin.exam`. Tests not run.

Shahriar signed in on `https://admin.symamlaw.com` in a browser and stayed signed in after a reload (5 November). Not checked: a staff mail link. Staff sign in again once, because cookies belonged to `admin.exam.symamlaw.com`.

Sandbox payments on the new hosts (Shahriar, 02:50 and 02:52 Dhaka, 5 November): two written exams, Muslim Law and Personal Law, 201 BDT each, orders `70413e032d2d4f5aa4e4e9138d39f6` and `1c9d241ced0943ee96affe594db823`. Each: session accepted, validation accepted, payment granted on the IPN (`POST …/ipn` 200 from SSLCommerz), then the browser return found it already granted. Risk level 0. Both grants are on the right exam. An older open order (`f6719f2fcf7c4db88586b8c5fc753d`, 00:40) was never paid; the recheck job finds nothing for it.

Afterwards the two test orders and their two grants were deleted (Shahriar's go), so the live Payments page starts without sandbox income. Dump first: `/var/backups/coaching/coaching-before-sandbox-test-cleanup-20261104-205917.dump`. One transaction, refused unless exactly those two orders of `shahriaranany6@gmail.com` and two grants matched. Left: the one `INITIATED` order of another student.

The 3 November "remove every account except the Master Admin" step is not repeated: the live database has no account older than 3 November besides the Master Admin. On 5 November it held the Master Admin, one System Admin (3 Nov) and six students who signed up from 3 November on, kept as real users.

Rollback (admin host): restore the unit and env backups, `daemon-reload`, restart `coaching-api`, put `/opt/coaching-web.before-20261104-204648` back and restart `coaching-web`, restore the `admin.exam` file from its backup, remove the `admin.symamlaw.com` link, `nginx -t`, reload. The env backup predates the live switch, so restore it only together with a return to sandbox.

## Live SSLCommerz store, 5 November 2026 (Dhaka)

At 21:05 UTC on 4 November (03:05 Dhaka) Shahriar edited `/etc/coaching-api.env`: `SPRING_PROFILES_ACTIVE=prod`, `SSLCZ_BASE_URL=https://securepay.sslcommerz.com`, and the live store id and password. `SSLCZ_CALLBACK_BASE_URL=https://symamlaw.com` unchanged; the old sandbox lines are kept as comments. Copy before the edit: `/etc/coaching-api.env.sandbox`. No secret value was printed or written anywhere else. Merchant panel findings: `reports/sslcommerz-status.md`.

The API restarted on `prod` in 16 s: 68 migrations validated, none applied, no data touched, Swagger off (404), `checkoutEnabled` true. No sitting was open. The preflight with `MODE=live SITE=symamlaw.com STAFF=admin.symamlaw.com` passed all 21 checks (its `SITE` default is now `symamlaw.com`).

From here a purchase on the site takes real money.

Live test (Shahriar, 5 November, Dhaka), on a test written exam "Payment EN" made for it:
- 03:15, 8.00 BDT order: SSLCommerz refused the session (503 to the browser, "payments aren't available"). The exam's price was 10 BDT with a 20% discount, below SSLCommerz's 10 BDT minimum. The order was cancelled on the next Buy. Follow-up: refuse a price that ends under 10 BDT when the exam is saved, or show a clear message.
- Order `6775cda08192479782376e5e038135`, 15.00 BDT, bKash. A wrong PIN failed on the bKash page; SSLCommerz held the transaction as `PROCESSING` and nothing was granted. Paid again on the same order with the right PIN at 03:23: validation accepted, payment granted, `POST …/ipn` 200 from SSLCommerz (`103.26.139.81`), browser return found it already granted, receipt PDF opened. Channel `BKASH-BKash`, risk 0.
- Refund from the merchant panel and unpublishing the test exam: see below.

Rollback to sandbox: copy `/etc/coaching-api.env.sandbox` back over `/etc/coaching-api.env` and restart `coaching-api`.
## Media features and payout encryption, 7 November 2026 (Dhaka)

API and website, Flyway 74 to 78. Commit `80c1f0a` (`demo/client-review`; code as merged in `de1c901`). Plan:
`../plans/media-release-deploy.md`. Released by the agent on Shahriar's go, 04:46 to 04:55 Dhaka.

- **Ships:** payout account numbers sealed (V75, plain columns kept and dual-written); notice photos and PDF (V76); student
  profile picture (V77); package routine PDF (V78); reviewer pictures on public and staff reviews; "Signed up" on the staff
  student page; discount tag on package cards; dark-mode fix; browse starts on the category's own exams.
- **Jar** SHA-256 `905bfb4e2f759b1ac8333e3adfea9510907e4eca85f568910cffce24aa60aba0`. Started in 24.7 s with
  `PAYOUT_BACKFILL_ENABLED=true`: 4 migrations applied (75 to 78), backfill sealed 1 account row (counts only logged),
  0 accounts left unsealed. Restarted with the backfill off: started in 15.0 s, no errors.
- **Payout key:** generated and stored by Shahriar (password manager plus an off-server copy), added to
  `/etc/coaching-api.env` by him as `PAYOUT_ENCRYPTION_KEY` with `PAYOUT_KEY_VERSION=1`. The agent checked only that the
  line exists once, is 44 characters and decodes to 32 bytes. The key was never shown to the agent.
- **Website** package SHA-256 `91f661e078baf4762bc5ef60dd958769af2f1a7b6b24731a76ffab070e6bdf37`, build
  `l23pI4LvrTr5TEztQhHTk`, swapped in at 22:50 UTC (04:50 Dhaka); up after 1 s. Previous release
  `/opt/coaching-web.before-20261106-225054`. The swap ran detached on the box (`nohup`) because the PC's link to the box
  dropped SSH connections all night.
- **Before:** an open sitting existed (a written exam "Bangladesh and International Affairs", started 02:13 Dhaka, after
  its window had closed at 17:10 the day before; deadline 05:13; no answer uploaded; no request for that exam since 02:15).
  Shahriar chose to deploy during it. No payment had started in the 15 minutes before. 1 payout account with a number.
- **Rehearsal** on a local restore of `coaching-pre-media-rehearsal-20261106-221509.dump`: passed (see the plan's status).
- **Backups (stamp `20261106-222202`):** `/var/backups/coaching/coaching-pre-media-20261106-222202.dump` (424 entries,
  SHA-256 `c15af6f3…77fe`), `files-pre-media-20261106-222202.tgz` (SHA-256 `f25ed879…3982`), both also in
  `F:\exam-vps-backups\`; `/etc/coaching-api.env.before-media-20261106-222202`;
  `/opt/coaching-api/api.jar.before-media-20261106-222202` (the `fdefac8` jar, SHA-256 `53ff62cd…c692`).
- **Checks:** live preflight (`MODE=live`) 0 failed of 21. Public: home, `/packages`, `/notice-board`, `/mcq`, `/written`,
  `/account`, the live package page and the admin login all 200; Swagger 404; the live package's pricing (30% off) and
  `routinePdf: null` in the API; the routine PDF route 404 with none uploaded. API log since the release: 0 errors, 0
  5xx responses, no stored payout number anywhere in it; 4 warnings from PDFBox's font loader.
- **Not checked by the agent** (needs the Master Admin's sign-in): the payout account screen (masked and full number), the
  staff reviews page, a student's "Signed up" line, and a draft notice with a photo and a PDF in the staff preview.

Rollback:
- API: stop `coaching-api`, copy `api.jar.before-media-20261106-222202` over `api.jar` and
  `coaching-api.env.before-media-20261106-222202` over `/etc/coaching-api.env`, start it. The previous jar starts on Flyway
  78 and reads the plain payout columns (proved in the rehearsal). If a payout account is changed while the previous jar
  runs, clear that account's sealed columns before the new jar goes out again.
- Website: stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-20261106-225054` back, start it.

## Direct enrolment installments and package add-exams, 9 November 2026 (Dhaka)

API and website, Flyway 78 to 80. Commit `9bb0ec6` (`demo/client-review`; code as in `8331118`, the commit adds only the release
plan). Plan: `../plans/direct-enrolment-release.md`. Released by the agent on Shahriar's go, 01:28 to 01:46 Dhaka.

- **Ships:** direct enrolment with full price, installments or No charge on exams and packages; a receipt per payment; the
  Installments page and notification tab; 06:30 Dhaka reminders; roster and direct grant can no longer both give access to a
  package; overlap warnings; exam-time email on direct enrolment; the package add-exams picker and bulk add (up to 500); the
  package page loads every exam; package card price wraps.
- **Tests on the release commit:** full API suite 1,042 tests, 0 failures, on a fresh database; full web suite 256 files, 1,742
  tests, all passing. Shahriar reports having read the Bangla copy. ESLint on the changed files: 3 errors, 1 warning, none
  affecting behaviour, not fixed in this release: `module` as a variable name in `staff/enrolments/page.tsx:41` and
  `roster-tab.tsx:27`, a state update inside an effect in `my-exams-page.tsx:41`, and an unused `locale` in
  `notifications-page.tsx:34`.
- **Not done before release, by Shahriar's choice of "now":** the browser walkthrough (plan 10.3) and the regression checks
  (10.4).
- **Jar** SHA-256 `02e95c5c975fea936a9a90b32433786cc8d436e0383eed671ecc3fcf0cab910c` (`F:\cc-releases\api-9bb0ec6.jar`). Restarted at
  19:43:10 UTC, started in 20.5 s, 2 migrations applied (79 and 80).
- **Website** package SHA-256 `f8784d6a8a9ce746d93e70b2bfb127ba31ae8bd6a2c93d66209d64d95714e55b`
  (`F:\cc-releases\web-9bb0ec6-20261108-191842.tgz`), build `_9SBs6JXXApVis45dME5J`, swapped in at 19:45 UTC (01:45 Dhaka); up after
  1 s. Previous release `/opt/coaching-web.before-20261108-194517`. Both steps ran detached on the box because the PC's link
  dropped SSH connections.
- **Before:** no open sitting and no payment started in the last 15 minutes, read immediately before the backups and again
  immediately before the API restart.
- **Rehearsal** on a local restore of `coaching-pre-installments-rehearsal-20261108-190606.dump` (SHA-256 `a8cacbdc…85f8`), with the
  `dev` profile, no mail or payment credentials and a throwaway payout key: passed. 2 migrations applied, row counts unchanged in
  6 tables. On the copy: enrolment with a plan, second payment, receipt PDF (97 KB), void, the Installments list, exam-time and
  receipt emails queued (logged, not sent), notices, both roster refusals; bulk add of 34 exams on a draft (withdrawn and missing
  skipped, repeat skipped, 501 ids refused), and a bulk add on a published package staged until re-publish. The previous jar
  `80c1f0a` then started on the upgraded copy, kept the student's access and notices, and marked 2 queued installment emails
  `SKIPPED`. The new jar restarted clean with the plan intact. The rehearsal database, its files and the throwaway key were removed.
- **Backups (stamp `20261108-192841`):** `/var/backups/coaching/coaching-pre-installments-20261108-192841.dump` (440 entries,
  SHA-256 `0b05b8f7…c3df`), `files-pre-installments-20261108-192841.tgz` (SHA-256 `22491b57…23e0`), both also in
  `F:\exam-vps-backups\`; `/etc/coaching-api.env.before-installments-20261108-192841`;
  `/opt/coaching-api/api.jar.before-installments-20261108-192841` (the `80c1f0a` jar, SHA-256 `905bfb4e…0aba0`).
- **Checks:** live preflight (`MODE=live`) 0 failed. Students, orders, grants, notices and mail rows unchanged (90, 5, 3, 33, 11);
  the 3 new tables empty. Public home, `/packages`, `/notice-board`, `/mcq`, `/written`, `/login` and the admin login all 200; the
  installment and bulk routes 401 without a sign-in; Swagger 404. API log since the release: 0 errors, 0 warnings, 0 5xx.
- **Not checked by the agent** (needs the Master Admin's sign-in): the add-exams picker on a draft package; a direct enrolment
  with the test account `shahriaranany6@gmail.com`, with real receipt and exam-time emails; the roster refusal in the browser.
- **To watch:** the first reminder job at 06:30 Dhaka on 9 November; with no plans it should do nothing.

Rollback:
- API: stop `coaching-api`, copy `api.jar.before-installments-20261108-192841` over `api.jar`, start it. The previous jar runs on
  Flyway 80 (proved in the rehearsal). New tables stay; queued installment emails are skipped; payments recorded stay in the
  database, but the old screens cannot show them.
- Website: stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-20261108-194517` back, start it. Roll
  the website back first if direct enrolment misbehaves, so nobody can record more payments.

## Offline admission, results, instructor pay, Finance, written drafts, exam mail off, 15 November 2026 (Dhaka)

API and website, Flyway 80 to 99. Release code `c1e7d54` (`demo/client-review`), plan
`../release-plan-15-nov.md`. Released by the agent on Shahriar's go, 14:08 to 14:11 Dhaka, **during a live written exam
(14:00 to 17:10), by Shahriar's explicit choice**; one sitting was open at the time.

- **Ships** (19 migrations, V81 to V99):
  - offline admission with the switch **on** (`OFFLINE_ADMISSION_ENABLED=true` added to `/etc/coaching-api.env`, web built
    with `NEXT_PUBLIC_OFFLINE_ADMISSION=1`): desk and public admissions, roll numbers, fees, routines with the wrong-date rule,
    offline results, instructor pay;
  - the Finance page;
  - written answers saved as drafts and submitted at the deadline, with the upload fixes;
  - no exam-time or hour-before mail (receipts and purchase notices still sent, from a background worker);
  - System Admin pricing; WhatsApp links; notice link words.
- **Tests on the release code:** full API suite 1,267/1,267 on a fresh database; full web suite 2,151/2,151; typecheck clean.
  The browser and phone checks were dropped by Shahriar's choice.
- **Rehearsal** on a dump of live (13:44 Dhaka), dev profile, no mail or payment credentials, jobs off, offline on:
  - 19 migrations in 0.38 s, 0 errors;
  - row counts identical in 10 tables;
  - smoke 16/16;
  - the previous jar `9bb0ec6` starts on the upgraded copy.
- **Production build** caught missing Suspense boundaries on the five new pages (fixed in `c1e7d54`).
- **Jar:**
  - `F:\cc-releases\api-c59a881.jar`, SHA-256 `e1e0bcb7dadcda86a43ca0fc46af3e0a43f3c75e211782088190c829cddbf793` (same API code
    as `c1e7d54`).
  - Restarted at 08:10:17 UTC, started in 20.0 s, 19 migrations applied, 0 failed.
- **Website:**
  - `F:\cc-releases\web-c1e7d54-20261115-080231.tgz`, build `Y5y_KkpDafToUJat8uHxb`, swapped at 08:10:55 UTC, up after 1 s.
  - Previous release `/opt/coaching-web.before-20261115-081055`.
- **Backups** (stamp `20261115-080828`), all also in `F:\exam-vps-backups\` and checked by SHA-256:
  - `/var/backups/coaching/coaching-pre-offline-20261115-080828.dump` (464 entries, `f7e6fdf9…3e55`);
  - `files-pre-offline-20261115-080828.tgz` (`5141fd5f…6c31`);
  - `/etc/coaching-api.env.before-offline-20261115-080828`;
  - `/opt/coaching-api/api.jar.before-offline-20261115-080828` (the `9bb0ec6` jar).
- **Checks after:**
  - Flyway 99, none failed; offline status `{"enabled":true}`.
  - Every public page and API checked answers 200; offline and finance routes 401 without a sign-in.
  - API log: 0 errors, 0 5xx; 4 warnings, all PDF font fallbacks (as before).
  - Data present: 201 users, 188 sittings, 18 answer scripts, 7 orders, 31 grants, 1,210 notices.
  - One password-reset mail to the named test account went out with no send failure; arrival to be confirmed by Shahriar.
- **Not checked by the agent** (needs the Master Admin's sign-in):
  - confirming a routine on the box, which renders the student copy on Linux;
  - the staff pages in a browser.

Rollback:
- **API:**
  1. Stop `coaching-api`.
  2. Copy `api.jar.before-offline-20261115-080828` over `api.jar` and `coaching-api.env.before-offline-20261115-080828` over the
     env.
  3. Start it. The previous jar runs on Flyway 99 (proved in the rehearsal); new tables and drafts stay, unused.
- **Website:** stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-20261115-081055` back, start
  it.

## Payment records, admission fixes, instructor pages, student dashboard, 17 November 2026 (Dhaka)

API and website, Flyway 103 to 106. Release code `f17be2a` (`demo/client-review`; the jar is built from `d90b4fe`, the same API
code). Released by the agent on Shahriar's go, 04:25 to 04:27 Dhaka, with no sitting open and no exam within 3 hours.

- **Ships** (3 migrations, V104 to V106):
  - the payment records page (every money stream, category and source filters, corrections the Master Admin finalizes);
  - the Admissions page no longer signs the Master Admin out; delete from batch frees the roll; Apply needs an account; cards show
    the course fee only;
  - the admission form (Session / Batch, NID and emergency number optional, same as present address); onboarding refuses only a
    phone or email of another person in the same batch; an account's Apply continues its earlier no-account application; the
    next form carries the last answers; a Master Admin password reset code;
  - instructor pages (add a class in or out of the routine, other payments, set the rate from a row, the rate beside each
    payment waiting for approval); exam batch routines;
  - the student dashboard, My offline batches, the phone menu, batch pages with tabs;
  - setup filled from the batch the student applied for; class delete; instalment rebalance.
- **Tests:** full API suite 1,287/1,287 on a fresh database; full web suite 2,214/2,214. Browser flows on the dev servers
  (online applicant, onboarding rules, walk-in, reset code, student pages in English and Bangla at desktop and phone width, staff
  fee approval and class delete, setup fix): all pass after the fixes.
- **Production build** caught a missing Suspense boundary on `/dashboard` (fixed in `f17be2a`).
- **Rehearsal** on the release backup, dev profile, no mail or payment credentials, jobs off, offline on: 3 migrations in about
  0.1 s, 0 errors; 11 table counts unchanged; read-only smoke 21/21 on real data; all 175 classes kept in the routine; the
  previous jar `e5ffa8c` starts on the upgraded copy (only the new routes answer 404).
- **Jar:** `F:\cc-releases\api-d90b4fe.jar`, SHA-256 `c11a1da92920eef64d04499b6527e252c08b7e582e04db30fbe6c349ef965698`.
  Restarted at 22:25:24 UTC, started in 26.0 s, 3 migrations applied, 0 failed.
- **Website:** `F:\cc-releases\web-f17be2a-20261116-221504.tgz` (SHA-256 `9e769f85…bb16`), build `M5eWnloNdR1LIxtsafkXk`,
  swapped at 22:26 UTC, up after 1 s. Previous release `/opt/coaching-web.before-20261116-222608`.
- **Backups** (stamp `20261116-221700`), also in `F:\cc-releases\backup-pre-records-20261116-221700\`, checked by SHA-256:
  - `/var/backups/coaching/coaching-pre-records-20261116-221700.dump` (581 entries, `eb0c473c…56e1`);
  - `files-pre-records-20261116-221700.tgz` (`3308de3e…9b41`);
  - `/etc/coaching-api.env.before-records-20261116-221700`;
  - `/opt/coaching-api/api.jar.before-records-20261116-221700` (the `e5ffa8c` jar, `d627f79c…`).
- **Checks after:** Flyway 106, none failed; offline status `{"enabled":true}`; every public and staff page checked answers 200;
  payment records, other payments, offline and finance routes 401 without a sign-in; API log 0 errors, 0 5xx, 4 warnings (PDF
  font fallbacks, as before); counts unchanged (219 users, 200 sittings, 19 answer scripts, 7 orders, 32 grants, 1,303 notices,
  39 admissions, 175/175 classes in the routine).
- **Not checked by the agent** (needs the Master Admin's sign-in): the new staff pages on live with real data; a real onboarding.
  Mail is still not delivered (Brevo), so join and reset codes are handed over by the office.

Rollback:
- **API:** stop `coaching-api`, copy `api.jar.before-records-20261116-221700` over `api.jar`, start it. The previous jar runs on
  Flyway 106 (proved in the rehearsal); the new tables stay, unused.
- **Website:** stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-20261116-222608` back, start it.

## PDF viewer fix, 22 November 2026 (Dhaka)

API and website, no migration (Flyway stays at 106). Release code `95705ad` (`demo/client-review`). Released by the agent on
Shahriar's go, 02:27 to 02:28 Dhaka. The release was held from 01:53 until a live written sitting reached its 02:26 deadline
(settled as failed to submit; no draft had been uploaded). The only other sitting in the window, a retake, was already submitted.
No exam was due to go live within 6 hours. Plan and causes: [pdf-viewer-fix-plan.md](../plans/pdf-viewer-fix-plan.md).

- **Ships:**
  - the legacy pdf.js build (PDFs open on browsers older than about Chrome 140 and in Facebook/Messenger's in-app browser),
    plus pdf.js's decoders, CMaps, fonts and colour profiles served from `/pdfjs/`;
  - separate download and browser error messages, and anonymous failure reports to `POST /api/v1/public/client-events`
    (one `client_event` log line, nothing stored);
  - viewer download progress, cancel on close, a per-session file cache, and freeing far pages;
  - smaller viewing copies of scans over 3 MB (`<stored>.view.pdf`, made by one background thread that pauses ±10/5 minutes
    around deadlines, plus a 10-minute sweep); PDF routes stream from disk, and `?original=true` returns the upload.
- **Stage 0, earlier the same night (01:01):** nginx access log with `rt=` / `urt=`
  ([nginx-conf-timed-log.md](../deploy/nginx/nginx-conf-timed-log.md)).
- **Tests:** full API suite on a fresh database 1,299/1,299 (once `AuditLogIT` listed the new route); full web suite
  2,222/2,222. Release-settings check build passed.
- **Kept out:** the uncommitted offline-routine work and `V107` stayed in the working tree; both builds were made from the
  clean commit with that work stashed. The first jar build carried a stale `V107` from `target/`. It was rebuilt with
  `clean`, and the shipped jar's migrations end at V106.
- **Jar:** `F:\cc-releases\api-95705ad.jar`, SHA-256 `920ec382e3a525d3d53df9f93f46cd90f3f80f563af10dcb53081ac57e05c1eb`.
  Restarted at 20:27:49 UTC, started in 24.8 s, Flyway 106 with none failed, 0 errors.
- **Website:** `F:\cc-releases\web-95705ad-20261121-194840.tgz` (SHA-256 `ebb1d3e7…ad27`), build `qSqLubzkk2FAC5Sg9GG4g`.
  The dry run passed; swapped at 20:27 UTC and up after 1 s. Previous release: `/opt/coaching-web.before-20261121-202730`.
- **Backups** (stamp `20261121-195338`), also in `F:\cc-releases\backup-pre-pdf-20261121-195338\`, checked by SHA-256:
  - `/var/backups/coaching/coaching-pre-pdf-20261121-195338.dump` (599 entries, `77e2d70d…7f35`);
  - `files-pre-pdf-20261121-195338.tgz` (371 MB, `bfa499ca…fcc2`);
  - `/etc/coaching-api.env.before-pdf-20261121-195338`;
  - `/opt/coaching-api/api.jar.before-pdf-20261121-195338` (the `d90b4fe` jar, `c11a1da9…`, the same as the 17 Nov release).
- **Checks after:**
  - both sites answer 200;
  - the served worker is the legacy build, byte-identical (1,321,391 bytes), and `/pdfjs/` wasm, cmaps, fonts and
    `VERSION 6.4.299` answer 200;
  - `client-events` answers 204 and its line reached the log;
  - PDF routes answer 401 without a sign-in (also with `?original=true`);
  - 0 API errors and 0 5xx.
- **Not checked by the agent** (needs a staff or student sign-in): opening a large script on the admin site
  (`X-Pdf-Copy: view` once its copy exists; Download should give the original), and a question paper on an older phone.

Rollback (no schema change, so any order works):
- **API:** stop `coaching-api`, copy `api.jar.before-pdf-20261121-195338` over `api.jar`, start it. Copies can stay (the old
  jar ignores them) or be removed with `find /var/lib/coaching-api/files -name '*.view.*' -delete`.
- **Website:** stop `coaching-web`, move `/opt/coaching-web` aside, put `/opt/coaching-web.before-20261121-202730` back,
  start it.

### Incident and fix, 22 November 2026, 17:56 Dhaka: offline admission hidden by the PDF release

- **What happened:** the 02:27 website build (`qSqLubzkk2FAC5Sg9GG4g`) was made without `NEXT_PUBLIC_OFFLINE_ADMISSION=1`.
  The flag is compiled in at build time. `release-build.sh` did not set it; the 15 and 17 Nov releases had set it by hand. So
  the whole offline admission section was hidden on both hosts from 02:27 to 17:56 Dhaka: menus, `/admission` (the "page
  not found" message), the batch pages, and the staff offline pages. The API's offline switch stayed on
  (`{"enabled":true}`), and no data was touched. Reported by Shahriar.
- **Fix:** `afb9da0` makes `release-build.sh` refuse to build unless `OFFLINE_ADMISSION` is `1` or `0`, and pass it through.
  The website was rebuilt from `afb9da0` with `OFFLINE_ADMISSION=1`: `F:\cc-releases\web-afb9da0-20261122-115319.tgz`
  (SHA-256 `bb36a0e1…8c06`), build `Iafvqqf63YJvkzJCmLqpM`. Checked locally first (`/admission` renders, the home menu links
  to it). The dry run passed; swapped at 11:56 UTC, up after 1 s. Previous release: `/opt/coaching-web.before-20261122-115627`
  (the build without the flag). No API change.
- **Checks after:** `/admission` and the public batch page render; the home menu links to `/admission`; the staff admission
  pages answer 200; the PDF smoke checks are unchanged (legacy worker, `/pdfjs/` assets, client-events 204, PDF routes 401
  without sign-in, 0 errors, 0 5xx).
- **Browser check (no sign-in), Chromium 156, desktop and phone size:** the public batch page's routine PDF opens in the new
  viewer and draws its 3 pages, with no console errors.
- **Website rollback now:** put `/opt/coaching-web.before-20261121-202730` back (the 17 Nov site, without the PDF fix).
  Do not use `.before-20261122-115627`: that build hides offline admission.

## Emergency move to the new VPS, 26 November 2026 (Dhaka)

The old box (`srv2089893`, `187.52.115.111`) stopped answering on ports 22 and 443 at about 18:20 Dhaka on 26 November.
Its Hostinger subscription had ended (auto-renew was off, renewal payment failed). Plan written the day before:
[../plans/vps-move-plan.md](../plans/vps-move-plan.md); the calm cutover in it was replaced by a restore from backup.

- **New box:** `srv2197598.hstgr.cloud`, `72.62.69.214`, Hostinger KVM 2 on a 24-month plan in the client's own account.
  Ubuntu 24.04.5, 2 vCPU, 8 GiB, 96 GB, 2 GiB swap, UTC. SSH alias `exam-kvm` (and `symam-vps`) now points here, key
  `symam_vps_ed25519`; the old box is `old-exam-kvm`. SSH is key-only. UFW 22/80/443, fail2ban, unattended upgrades.
- **Same stack:** OpenJDK 21.0.13, PostgreSQL 16.15, Node 22.23.5, nginx 1.24, certbot 2.9.
- **Restored from backup** (`F:\exam-vps-backups\full-20261125-054244`): database `db-20261125-095400.dump` (25 Nov 15:54
  Dhaka, SHA-256 `37ac11ed…a44e`), uploaded files and config of 25 Nov 11:42 (no file changed between the two), the running jar
  (`fca58170…`) and website build `pilnQf0pqE7uQMtXVesE3` copied as they were, the env file with every secret, the four
  symamlaw nginx sites, and `/etc/letsencrypt` (the `nip.io` and `hstgr.cloud` staging names were dropped). A default server
  refuses unknown host names.
- **Lost:** everything written between 25 Nov 15:54 and the shutdown on 26 Nov. SSLCommerz showed no online payment in that
  window. Office entries for that window are re-entered by hand; students who need it are onboarded manually.
- **Started** 12:51 UTC: 107 migrations validated, none applied, 0 errors. 272 users, 9 orders, 285 files.
- **DNS** (Namecheap, by Shahriar): `@`, `www`, `admin`, `exam`, `admin.exam` A records to `72.62.69.214`, about 13:10 UTC;
  seen at Namecheap, 1.1.1.1 and 8.8.8.8. Mail records unchanged.
- **Checks:** live preflight (`MODE=live`, new `BOX_IP` default) 0 failed of 21; `certbot renew --dry-run` succeeds for all
  four certificates (it failed on the old box); every public page and redirect answers as before.
- **Live payment:** order `e1278da7a1504db69454947f99f733`, 15.00 BDT, 19:34 Dhaka: IPN from SSLCommerz 200, validation
  accepted, granted; the browser return found it already granted.
- **Mail is refused:** Brevo answers `525 5.7.1 Unauthorized IP address`. `72.62.69.214` must be added in Brevo, Security,
  Authorised IPs (left for later by Shahriar). Until then no code, reset or receipt mail goes out.
- **Backups:** the nightly job (`/usr/local/bin/coaching-backup`, 02:30 Dhaka, 14 days on the box) is installed. New: the PC
  pulls it daily with `F:\exam-vps-backups\tools\pull-backups.ps1` (scheduled task "SYMAM backup pull", 10:00 or the next
  time the PC is on): the newest database dump checked by SHA-256 (last 30 kept) and an incremental mirror of the uploaded files
  in `F:\exam-vps-backups\nightly\`. Log: `pull.log` there. Object storage is still open.

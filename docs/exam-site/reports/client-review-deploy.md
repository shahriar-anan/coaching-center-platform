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

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

Basic auth guards the pages of both hosts. The website sends `Authorization: Bearer …` on every API call, which replaces the browser's saved basic-auth login, so `/api/` is guarded by a gate cookie instead: a response that passed basic auth sets `review_gate`, and `/api/` answers 401 without it. The cookie is added only to successful responses (no `always`). The two SSLCommerz callbacks (`…/live-exam-orders/{tranId}/ipn` and `/return`) are open, because the gateway's server can send neither. Without the cookie, signing in as a demo account through the API directly is not possible.

## Checked on the live hosts

| Check | Result |
|---|---|
| Pages and `/api/` without a password | 401 on both hosts |
| Wrong password | 401 |
| Gate cookie set only after basic auth, never on a 401 | yes |
| `/api/` with the cookie, with a wrong cookie | 200, 401 |
| `ipn` and `return` callbacks without a password | reach the API (404 for an unknown order) |
| Student host `/staff`; admin host `/`, `/register`, `/mcq` | redirected to the other host / to `/staff` as designed |
| Sign-in, all five accounts, by phone and email | roles SYSTEM_ADMIN, INSTRUCTOR, STUDENT x3 |
| Instructor | sees the written and mixed packages; refused on the MCQ package |
| System Admin payments | 3 paid, 1 failed |
| Student A | 4 unread notices |
| Public catalogue (signed out) | new categories and packages |
| Gate secret rotated after it appeared in test output | done, re-verified |

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

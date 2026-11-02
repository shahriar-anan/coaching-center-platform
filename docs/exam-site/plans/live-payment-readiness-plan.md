# Live payment readiness plan

2 November 2026. **Audience:** Shahriar and the agent helping him. **Outcome:** the exam site runs on the client's domain, `exam.symamlaw.com` for students and `admin.exam.symamlaw.com` for staff, and one real SSLCommerz payment has been taken, granted and refunded. The old site keeps `symamlaw.com`, `www`, `admin` and `backoffice`.

The how-to for each step is in [live-domain-and-sslcommerz.md](live-domain-and-sslcommerz.md). This file says what is left, who does it and in what order. No store id, store password or other secret belongs in either file.

## Where things stand

Checked on the box on 2 November 2026, read-only:

| | |
|---|---|
| Server | `exam-kvm`, `187.52.115.111`, profile `staging`, sandbox store, callback `https://srv2089893.hstgr.cloud` |
| Hosts | student `srv2089893.hstgr.cloud`, admin `admin.187-52-115-111.nip.io`, both with valid certificates |
| Website and API | the release of 30 October. Database at Flyway 55; the branch has migrations up to V64, so the current code is not deployed |
| Data | review data: 7 students, 2 instructors, 1 System Admin, the Master Admin; 4 paid demo orders (`DEMO…` tran ids), 1 failed |
| Env file | `SEED_DEMO=false`. `MASTER_ADMIN_PASSWORD` and the unused `MAIL_TEST_ENABLED` are still there |
| DNS | Namecheap nameservers. `exam.symamlaw.com` resolves to the old server only through the `*.symamlaw.com` wildcard |
| Preflight | `deploy/live-payment-preflight.sh` passes every check against the current sandbox hosts |

## Already done in the repo (kept as is)

| Item | Files | State |
|---|---|---|
| A repeat Buy after a price or discount change opens a new order at the current price, and cancels the old one. Before, SSLCommerz was sent the old amount | `LiveExamOrderService.java`, `LiveExamPurchaseIT.java` (new test), `current-api.md` | Written. **Test not run** (no test runs until Shahriar says). Not committed |
| Nginx sites for the two new hosts, copies of the live ones with only `server_name` changed | `docs/exam-site/deploy/nginx/` | Ready to install |
| Read-only preflight for a sandbox or live test | `docs/exam-site/deploy/live-payment-preflight.sh` | Run once against the current hosts: 0 failed |

## Payment code review, 2 November 2026

Read: `SslCommerzGateway`, `SslCommerzProfileGuard`, `LiveExamOrderController`, `LiveExamOrderService`, `LiveExamOrderTx`, `LiveExamPaymentRecheck`, `WebCorsConfig`.

Sound and left alone: access is granted only after the validation API returns `VALID`/`VALIDATED` with matching tran id, amount, currency and store id; the browser redirect alone grants nothing; a repeat IPN writes nothing; `FAILED` and `CANCELLED` are not final, so money that SSLCommerz validates later still grants; tran ids are 30 random hex characters; the recheck job covers a lost IPN; no secret is logged.

| Finding | Decision |
|---|---|
| Repeat Buy charged the old price | Fixed (above) |
| `staging` refuses the live host and `prod` refuses the sandbox host (`SslCommerzProfileGuard`) | By design. A live payment needs `SPRING_PROFILES_ACTIVE=prod` |
| CORS and the payment return page follow `WEB_ORIGIN` (first entry = student host, second = admin host, also used in mail links) | Config only. No code change needed for the new hosts |
| `/ipn` answers an unauthenticated caller with the order view, including the receipt email | Left. The tran id cannot be guessed and the response is a documented contract. Possible later hardening |
| `risk_level` from SSLCommerz is not checked; risky payments are granted | Open decision (`decisions.md`, Still open) |
| No refund through the API | Known. Refunds go through the merchant panel |

## Remaining work, in order

| # | Step | Who | Needs first | Done when |
|---|---|---|---|---|
| 1 | Decide: host names (default above), the live test date, that the review data may be cleared, and whether `risk_level` stays as is | Shahriar, with the client | — | written in `decisions.md` |
| 2 | Find out whether the old site and the exam site share the live store, and get SSLCommerz to confirm the callback rules (guide, phase 6) | Client, SSLCommerz | 1 | written answer from SSLCommerz |
| 3 | Run the API tests for the repricing change (`LiveExamPurchaseIT`, `BundleOrderIT`), then commit | Agent, on Shahriar's word | — | tests pass, committed |
| 4 | Release the current branch to the box on today's hosts: backups, jar, website (Flyway 55 → current) | Agent, with Shahriar's OK | 3 | preflight `MODE=sandbox` with today's hosts passes |
| 5 | Add the two A records in Namecheap | Shahriar | 1 | both names resolve to `187.52.115.111` |
| 6 | Install the two Nginx sites and issue certificates | Agent, with OK | 5 | both hosts answer over HTTPS |
| 7 | Move the app to the new hosts: env, web service, website build | Shahriar (env), agent (rest) | 6 | preflight `MODE=sandbox` passes with the new hosts |
| 8 | One sandbox payment on the new hosts, with its IPN | Shahriar in the browser, agent on the logs | 7 | `…/ipn status=200` and a grant |
| 9 | Clear the review data and tidy the env file | Agent, on Shahriar's explicit go | 1, 8 | no demo accounts or `DEMO…` orders; two env lines gone |
| 10 | Live store panel settings, if SSLCommerz asks for any | Client | 2, 7 | per SSLCommerz's answer |
| 11 | Switch to `prod` and the live store | Shahriar (env), agent (restart, checks) | 8, 9, 10 | preflight `MODE=live` passes |
| 12 | Live test: one wrong-OTP payment, one good payment (card or bKash), refund in the panel | Shahriar in the browser, agent on the logs | 11 | the checks in guide phase 8 all pass |

Steps 2 and 3 can run while the others wait. Nothing after step 4 starts without step 4.

## Not in this plan

- Pointing `symamlaw.com` or `www` at the new server. The old site keeps them.
- Google sign-in on the new hosts (`NEXT_PUBLIC_GOOGLE_ENABLED=false` today).
- Refunds through the API, a smaller `/ipn` response, a `risk_level` rule.
- Course and book payments on the old site.

## Rollback

Each step is undone in the guide's rollback table. The short form: back to sandbox is the previous env file plus a restart; back to the old hosts is the previous env file, the previous web service unit and the previous website release; the old hostnames keep working throughout because their Nginx sites and certificates are not removed.

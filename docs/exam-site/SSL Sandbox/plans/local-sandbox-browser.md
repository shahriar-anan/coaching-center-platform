# Local sandbox browser

Rules: `../rules/local-sandbox-browser.md`. Tests: `../tests/local-sandbox-browser.md`.

No new Flyway version. No new table. Package `com.coachingcenter.api.exams`. The grant rule does not change: access is written only after the validation API returns `VALID` or `VALIDATED` and the transaction id, amount, currency, and store id match.

Automated tests use `FakeLiveExamPaymentGateway` or a mocked `fetch`. They never open a socket to `sandbox.sslcommerz.com` or `securepay.sslcommerz.com`.

Do slices 1–5 in order. Each slice exits on its own tests. Slice 6 creates the data. Slice 7 is Shahriar's browser pass.

## Slice 1 — Checkout that cannot strand an order

### 1a. Check the gateway before the insert

`LiveExamOrderService.initiate` inserts the order and only then looks up the gateway. With no `SSLCZ_*` values, every Buy leaves an `INITIATED` row and returns `503`.

Check `gateways.getIfAvailable()` before `tx.insert`. If it is absent, return `503` `PAYMENT_GATEWAY_UNAVAILABLE` and write no row. A repeat of an existing open order with no gateway also returns that `503` and changes nothing.

### 1b. A rejected session names the order

If `start` throws after the row exists, the student today gets `503` with no id. The next Buy reuses the same `tran_id`, the session fails again, and nothing on the page can cancel it.

When `start` fails on a new order or on a repeat of an open order:

- The order stays `INITIATED`. No grant.
- The response is `503` `PAYMENT_GATEWAY_UNAVAILABLE`.
- The body is the normal error body plus `orderId` and `tranId`. `ApiError` gains two nullable fields. The global `non_null` setting keeps them off every other error.
- The log line stays `sslcommerz session rejected tranId=<tranId> error=<class>`. No gateway body, URL, store id, or password.

A first session that succeeds is still `201` with `redirectUrl`. A repeat whose session succeeds is still `200` with the same `tranId` and a `redirectUrl`.

`FakeLiveExamPaymentGateway` gains a mode in which `start` throws the gateway-unavailable exception.

### 1c. Trim the four settings

`SslCommerzProperties` trims `storeId`, `storePassword`, `baseUrl`, and `callbackBaseUrl`. Spring keeps trailing spaces in a `.properties` value, and `api/.env` is read as `.properties`. A trailing space in the password would be sent to SSLCommerz. The store-id comparison already trims.

### 1d. Name the settings in the example

`api/.env.example` gains, with empty values and a comment that the live store never goes in a dev file:

```
SSLCZ_STORE_ID=
SSLCZ_STORE_PASSWORD=
SSLCZ_BASE_URL=
SSLCZ_CALLBACK_BASE_URL=
```

### Tests

`LiveExamPurchaseIT`:

- No gateway bean: `503`, and the order count for that student is unchanged. This needs a context without the fake gateway, or a fake mode that makes the provider return nothing. Pick whichever keeps the other tests on the cached context.
- `start` rejected on a new order: `503`, `orderId` and `tranId` present, no `redirectUrl`, database status `INITIATED`, zero grants. Cancel of that `orderId` is `200` `CANCELLED`. The next Buy is `201` with a different `tranId`.
- `start` rejected on a repeat: same `orderId` and `tranId` as the open order, `503`.
- A rejected-session body does not contain the store id, the store password, or `https://`.

A unit test for `SslCommerzProperties` shows that surrounding spaces are removed.

Exit: those tests pass. `api/.env.example` has the four names and no value.

## Slice 2 — A return without `val_id` asks SSLCommerz once

Today a fail or cancel return usually carries no `val_id`. The order stays `INITIATED`, the result page shows Pending for 60 seconds, and only the 10-minute job can mark it. The browser test would never show Failed.

When the browser return has no `val_id`, call `gateway.lookup(tranId, amount, currency)` once, in the request:

| Lookup | Result |
|---|---|
| `PAID` | Call `verify` with the `val_id` from the lookup. Grant only if that validation matches. |
| `FAILED` | `tx.mark(tranId, FAILED)`. No grant. |
| `CANCELLED` | `tx.mark(tranId, CANCELLED)`. No grant. |
| `NONE` | Leave `INITIATED`. |
| `UNREACHABLE` | Leave `INITIATED`. Still redirect. |

The posted `status` is still ignored. `tx.mark` already refuses to change a `SUCCEEDED` order. The redirect to `/purchase/result` is unchanged in every row. An exception from the lookup is logged by class name only and the redirect still happens.

The IPN keeps its current behavior. SSLCommerz cannot reach this PC, so this slice does not change what the local test can prove about it.

`FakeLiveExamPaymentGateway.lookup` gains settable results.

`LiveExamPurchaseIT`: one case per row above, through the browser return, asserting the database status, the grant count, and the `302` location. A `PAID` lookup whose validation is invalid ends `FAILED` with no grant.

Exit: those tests pass.

## Slice 3 — `/sandbox`

`web/src/app/sandbox/page.tsx`, outside the admin layout. A test page, not the student product. No design work.

`allowsStudent` in `web/src/lib/auth/session-context.tsx` adds `/sandbox`. The admin `/login` page still rejects a student. `RequireAuth` still keeps a student out of admin pages.

`ApiErrorBody` and `ApiError` in `web/src/lib/api` gain optional `orderId` and `tranId`.

### Sign-in

Anonymous: a phone-or-email field, a password field, and the existing `login` from the session context. Signing in keeps the page on `/sandbox`. A signed-in non-student sees that this page is for a student account and no Buy button.

### List

1. `GET /api/v1/public/live-exam-modules?size=100`.
2. For each module, `GET /api/v1/public/live-exam-modules/{id}/exams?size=100`.
3. Show an exam only when `price` is present. Show its English title and price as text.
4. Nothing listed: say that no priced exam is published yet.

### Buy

`POST /api/v1/live-exam-orders` with `{ "examId": "<id>" }`. Nothing else in the body.

| Response | Page |
|---|---|
| `201` or `200` with `redirectUrl` | Save `{ orderId, tranId, examId, examName }` in `sessionStorage` key `sandbox-order`, then `window.location.assign(redirectUrl)`. |
| `503` with `orderId` | Save the same record. Stay. Say the payment page did not open. Show Cancel. |
| `503` without `orderId` | Stay. Say payments are not configured on this API. Save nothing. |
| `409` `ALREADY_PURCHASED` | Say this account already has the exam. |
| `403` `PHONE_REQUIRED` | Say the account needs a phone number. |
| `404` | Say the exam is not on sale any more, and reload the list. |
| `409` `PRICE_REQUIRED` | Same as `404`. |

Buy is disabled while a request is in flight, so a double click sends one request.

### Pending order

On load, when `sandbox-order` exists and the student is signed in, read `GET /api/v1/live-exam-orders/{orderId}`.

- `INITIATED`: show the exam name and Cancel.
- `SUCCEEDED`, `FAILED`, `CANCELLED`: clear `sandbox-order`. Show nothing for it.
- `404`: clear it. It belonged to another account.

Cancel calls `POST /api/v1/live-exam-orders/{orderId}/cancel`. `200` clears the key. `409` `PAYMENT_NOT_PENDING` clears the key and says the order is no longer pending. Cancel never navigates.

### Tests

Component tests, mocked `fetch`, no API process:

- An exam without `price` is not shown.
- The Buy body is exactly `{ "examId": ... }`.
- `redirectUrl` is assigned. `503` with `orderId` is not followed and shows Cancel.
- `503` without `orderId` stores nothing.
- A stored order that reads `SUCCEEDED` is cleared and shows no Cancel.
- Cancel sends the right path and does not navigate.
- A student signing in on `/sandbox` is not redirected to `/students`.

Exit: `npm test`, `npm run lint`, and `npm run build` pass in `web`.

## Slice 4 — Dev seed, still opt-in

`StagingExamSeed` runs on `staging` or `dev` when `app.seed.enabled=true`. It still refuses when `prod` is active, including dev and prod together.

`application-dev.properties` adds `app.seed.enabled=${SEED_ENABLED:false}`.

The test JVM uses the dev profile by default and loads `api/.env` when run from `api/`. That file may contain `SEED_ENABLED=true` and the sandbox store. `PostgresIntegrationTest` adds `app.seed.enabled=false`, beside the blank `SSLCZ_*` it already sets. Dynamic properties win over `api/.env`.

`StagingExamSeedTest` gains: dev is allowed, prod is refused, dev plus prod is refused.

Do not start the API with `SEED_ENABLED=true` in this slice.

Exit: the unit test passes. `coaching_center_dev` has no seed row.

## Slice 5 — Full checks

Run the full API suite against `coaching_center_test`, once, with `api/.env` present. Run `npm test`, `npm run lint`, and `npm run build` in `web`.

The only known API failure before this plan was `BootstrapIT`, which is not on the payment path. Record any other failure with its cause. Fix payment-path failures before slice 6.

Exit: no payment-path failure.

## Slice 6 — Data

Only after slices 1–5 exit. Shahriar fills the `SEED_*` names in `api/.env` and sets `SEED_ENABLED=true`. He starts the API from `api/`. The seed creates one system admin, one student with a phone and a verified email, and one published MCQ titled `Staging practice exam` at 10.00 BDT in a published module and category.

He sets `SEED_ENABLED=false` and restarts. He opens `http://localhost:3000/sandbox`, signs in as the student, and sees that exam at 10.00. He does not pay in this slice.

Exit: the exam is listed. No order row exists.

## Slice 7 — Browser pass

Shahriar follows `../tests/local-sandbox-browser.md`. The agent may start `api` and `web` and hand over the URL. The agent does not open the payment page or type a card.

Exit: the checklist is filled in.

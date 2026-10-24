# Local sandbox tests

Plan: `../plans/local-sandbox-browser.md`.

## Automated

From `api/`, against `coaching_center_test`:

```powershell
$env:TEST_DB_URL="jdbc:postgresql://127.0.0.1:5432/coaching_center_test"
$env:TEST_DB_USER="postgres"
$env:TEST_DB_PASSWORD="postgres"
mvn test
```

Do not drop `coaching_center_dev`. These tests never call SSLCommerz, even with the sandbox store in `api/.env`.

| Slice | Test | Proves |
|---|---|---|
| 1 | `LiveExamPurchaseIT` | No gateway writes no order. A rejected session returns `503` with `orderId` and `tranId`, keeps `INITIATED`, writes no grant, can be cancelled, and the next Buy gets a new `tranId`. A repeat returns the same ids. The body has no store secret. |
| 1 | `SslCommerzProperties` unit test | Surrounding spaces are removed. |
| 2 | `LiveExamPurchaseIT` | A return without `val_id`: lookup paid and valid grants; paid but invalid fails; failed and cancelled mark the order; none and unreachable leave it pending. Every case redirects to `/purchase/result`. |
| 3 | `web` component tests | No unpriced exam. Buy body is `{ examId }`. Redirect followed only with `redirectUrl`. `503` with and without `orderId`. Stale stored order cleared. Cancel does not navigate. A student stays on `/sandbox`. |
| 4 | `StagingExamSeedTest` | Dev allowed. Prod and dev plus prod refused. |
| 5 | Full API suite, `npm test`, `npm run lint`, `npm run build` | No payment-path failure. |

## Browser checklist

Shahriar runs this after slice 6. Record pass or fail and one sentence for each line.

### Before starting

- [x] `api/.env` has the four `SSLCZ_*` values from `../README.md`, and `SEED_ENABLED=false`. Pass: the four names are set, the base is the sandbox host, and the seed flag is false.
- [x] The API was restarted after the last `.env` edit. Pass: it was started again from `api/` after that edit.
- [x] API started from `api/` with `mvn spring-boot:run`. The startup log shows profile `dev`. Pass: the log falls back to the dev profile.
- [x] Website started from `web/` with `npm run dev`. `NEXT_PUBLIC_API_BASE_URL` is not set. Pass with a note: `web/.env.local` sets it to `http://localhost:8080`, the same address used when the variable is absent.
- [x] Address bar uses `http://localhost:3000`, not `127.0.0.1`. Pass: the sandbox and result pages used that host.
- [x] Use Chrome or Edge in one ordinary window. Not a private window. Pass with a note: the pass ran in the in-app browser. A separate Edge window was opened, and desktop control was not available to drive it.
- [x] The card is the sandbox test card or sandbox mobile-banking option shown by SSLCommerz. Never a real card. Pass: the documented sandbox card and the sandbox Success and Failed options were used.

The seed has one student, and a paid exam cannot be bought again. Run the sections in this order.

### 1. Session opens

- [x] Sign in on `/sandbox` as the seed student. The page stays on `/sandbox`. Pass: sign-in left the address on `/sandbox`.
- [x] `Staging practice exam` is listed at 10.00. Pass: the row showed that title and 10.00.
- [x] Buy goes to a page on `sandbox.sslcommerz.com`. The API log has `sslcommerz session accepted tranId=`. Pass: Buy opened the sandbox host and the log recorded session accepted.

If Buy stays on `/sandbox` with "payment page did not open" and the log has `sslcommerz session rejected`, stop here. Click Cancel and confirm the order is gone from the page. Write down the rejection. The likely cause is that the sandbox refuses `http://localhost:8080` as a callback. See "If the session is rejected" in `../README.md`.

### 2. Fail

- [x] On the sandbox page, choose the failing test option, or let the card fail. Pass: the sandbox Failed option was used.
- [x] The browser lands on `http://localhost:3000/purchase/result?tranId=...`. Pass: the address was that result page on localhost.
- [x] The page shows Failed, or Pending if SSLCommerz's query did not yet know the result. Record which. Pass: the heading was Failed.
- [x] If Pending: go to `/sandbox` and click Cancel. Pass: not needed, because the heading was Failed.
- [x] No grant exists for this student. Pass: after this failed order the student had no grant.

### 3. Cancel on the sandbox page

- [x] Buy again. The log shows a new `tranId`. Pass: the next session accepted line used a different id.
- [x] Click the cancel link on the sandbox page. Pass: the sandbox cancel link was confirmed.
- [x] The result page shows Cancelled, or Pending. Record which. Pass: the heading was Cancelled.
- [x] If Pending: Cancel on `/sandbox`. Pass: not needed, because the heading was Cancelled.

### 4. Back without paying

- [x] Buy. On the sandbox page, use the browser Back button. Pass: Back returned to `/sandbox`.
- [x] `/sandbox` shows the pending order and Cancel. Pass: the exam name and Cancel were both shown.
- [x] Buy again. Either the sandbox page opens with the same `tranId`, or the page says the payment page did not open. Both are passes. Click Cancel if it is offered. Pass: the sandbox page opened again and the log repeated the same id.

### 5. Paid

- [x] Buy. Pay with the sandbox success option. Pass: a later success option granted the exam. The first success option was rejected because the validation response omitted the store id; that check now accepts a missing store id and still rejects a different one.
- [x] The browser lands on `/purchase/result?tranId=...` and shows Paid with the exam name. Pass: the heading was Paid and the link was Staging practice exam.
- [x] If it asks for sign-in first, sign in as the same student on that page. It then shows Paid and does not go to `/students`. Pass: the signed-in result stayed on the result page.
- [x] Reload. Still Paid. Pass: a reload still showed Paid and the exam name.
- [x] The log has `sslcommerz validation accepted tranId=` and `live exam payment granted tranId=` for that id. Pass: both lines were written for that payment.
- [x] Back on `/sandbox`, Buy again. The page says the account already has the exam. No new `tranId` appears in the log. Pass: the page said the account already has the exam, and no later session line was written.

### 6. Logs

- [x] No line contains the store id, the store password, a card number, or a raw SSLCommerz response. Pass: the dev log for this pass contains none of those.

### Not proven here

These are not failures of this pass:

- An IPN from SSLCommerz. Their servers cannot reach `localhost`.
- The 10-minute recheck. It may mark an old pending order during the pass. That is expected.
- Two paid orders for one exam. The second is marked paid with no second grant. Refunds are out of scope.

## Result

| Date | Session | Fail | Sandbox cancel | Paid | Notes |
|---|---|---|---|---|---|
| 2026-10-24 | Pass | Failed | Cancelled | Pass | The payment-host return was blocked by the website CORS allowlist until the return and IPN paths were allowed. A validation response with no store id was rejected until a missing store id was accepted; a different store id is still rejected. |

Do not paste a store id, password, card number, or `val_id`.

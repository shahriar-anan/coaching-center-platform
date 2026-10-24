# SSLCommerz status

24 October 2026. No store id, store password, or panel login is in this file. Those values stay in the gitignored `docs/exam-site/config-files/payment-credentials.md`.

## Status

The checkout API is written and covered by tests that never call SSLCommerz. One session request to the sandbox host succeeded. No card payment has been made, and the new server has no SSLCommerz settings.

| | |
|---|---|
| API code | Implemented. Flyway `V32`. On the new server the schema is at version 33, which includes this. |
| Automated tests | `LiveExamPurchaseIT` passes against a fake gateway. |
| Sandbox session | One request on 24 October 2026 returned a payment-page URL. No payment was completed. |
| Sandbox payment through this API | Not done. |
| Live store | Present in the old site's env. Not copied to the new server. Not used. |
| New server | `SSLCZ_STORE_ID`, `SSLCZ_STORE_PASSWORD`, `SSLCZ_BASE_URL`, and `SSLCZ_CALLBACK_BASE_URL` are absent. Starting an order there returns 503. |

## Which sandbox

The old site's env file has a commented sandbox store id and password, with `SSLCZ_TESTMODE=true`. That pair is not the store the old site uses. The active pair there is the live store (`SSLCZ_TESTMODE` false, host `https://securepay.sslcommerz.com`).

On 24 October 2026 the commented pair was sent once to `https://sandbox.sslcommerz.com/gwprocess/v4/api.php`, with the same fields this API sends (10.00 BDT, a throwaway `tran_id`, example.com as the return URLs). SSLCommerz answered `SUCCESS`, store name `Demo`, and a `GatewayPageURL` on `sandbox.sslcommerz.com`. No new SSLCommerz account was created. The payment page was not opened, no test card was used, and the validation API was not called.

## What the code does

`SslCommerzGateway` runs only when all four `SSLCZ_*` values are set.

- Start sends a form to `{SSLCZ_BASE_URL}/gwprocess/v4/api.php` and reads `GatewayPageURL`.
- The return and IPN addresses it sends are `{SSLCZ_CALLBACK_BASE_URL}/api/v1/live-exam-orders/{tranId}/return` and `.../{tranId}/ipn`. Both are public.
- A browser return of `success`, or an IPN, is accepted only after the validation API returns `VALID` or `VALIDATED` for that `val_id`, and the amount, currency (BDT), and `tran_id` match the order. The redirect alone does not grant access.
- A match writes one grant, `MODULE` or `EXAM`. A repeat notice does not write a second grant.
- `fail` and `cancel` grant nothing. If validation cannot be reached, the order stays `INITIATED`.

`LiveExamPurchaseIT` replaces the gateway with `FakeLiveExamPaymentGateway`. The fake redirect is `https://gateway.test/{tranId}`. The tests that passed cover price permissions, initiate, a repeated initiate, a verified grant, a repeat notice, an invalid verification, a cancel, an unreachable verification, roster access beside a purchase, and an exam published after a module purchase. They do not cover `SslCommerzGateway`.

## Not done

- A sandbox card payment, a fail, and a cancel against this API.
- A validation-API call with a real `val_id`.
- An IPN delivered by SSLCommerz. The merchant panel has not been opened, so it is unknown whether a fixed panel IPN would replace the per-payment `ipn_url`.
- The browser return still responds with JSON. There is no website result page.
- Live credentials on the new server, and any live payment.

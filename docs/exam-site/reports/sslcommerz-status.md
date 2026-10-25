# SSLCommerz status

24 October 2026. No store id, store password, or panel login is in this file. Those values stay in the gitignored `docs/exam-site/config-files/payment-credentials.md`.

## Status

**Updated 25 October 2026.** Sandbox payments now work end to end on the new server. The evidence is in [vps-staging-deploy.md](vps-staging-deploy.md), slice 8. The sections below "Which sandbox" are the 24 October record.

| | |
|---|---|
| API code | Implemented. Flyway `V32`. `V37` stores the payment channel. The server schema is at `37` after the next deploy (`36` today). |
| Automated tests | `LiveExamPurchaseIT` passes against a fake gateway. `SslCommerzGatewayTest` covers the real gateway class against a local stub. |
| Sandbox payments through this API | Done on `https://srv2089893.hstgr.cloud`: VISA test card and bKash succeeded with an IPN, plus a failed and a cancelled order. Receipt mail sent. |
| IPN | Delivered to the per-payment `ipn_url`. No panel IPN URL overrides it on the sandbox store. |
| Live store | Present in the old site's env. Not copied to the new server. Not used. |
| New server | `staging` profile with the sandbox store and `SSLCZ_CALLBACK_BASE_URL=https://srv2089893.hstgr.cloud`. |

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

`LiveExamPurchaseIT` replaces the gateway with `FakeLiveExamPaymentGateway`. The fake redirect is `https://gateway.test/{tranId}`. The tests that passed cover price permissions, initiate, a repeated initiate, a verified grant, a repeat notice, an invalid verification, a cancel, an unreachable verification, roster access beside a purchase, an exam published after a module purchase, and the staff order list. `SslCommerzGatewayTest` covers `SslCommerzGateway` itself against a local HTTP stub, including the payment channel.

## Not done

- Live credentials on the new server, and any live payment. At launch: one wrong-OTP payment and one good payment on the live store, then a refund of the good one from the merchant panel (`todays-goals.md` section 5). That check also shows whether the live panel overrides the per-payment `ipn_url`.
- Refunds through the API. Not planned for now.

Done on 25 October 2026, previously listed here: sandbox card, fail and cancel payments, validation with a real `val_id`, an IPN from SSLCommerz, and the website result page.

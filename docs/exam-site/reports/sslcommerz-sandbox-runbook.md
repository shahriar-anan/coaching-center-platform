# SSLCommerz sandbox runbook

24 October 2026. No store id, store password, or card number belongs in this file.

Use the sandbox host only. Do not point this run at `https://securepay.sslcommerz.com`, and do not copy the live store into the server environment.

## Environment

Set these on the API process. Leave them unset and checkout returns 503.

| Name | Sandbox value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `staging` |
| `SSLCZ_STORE_ID` | The sandbox store id |
| `SSLCZ_STORE_PASSWORD` | The sandbox store password |
| `SSLCZ_BASE_URL` | `https://sandbox.sslcommerz.com` |
| `SSLCZ_CALLBACK_BASE_URL` | The public `https` origin of this API, with no path and no trailing slash. For the new server that is `https://srv2089893.hstgr.cloud` until the domain is cut over. |
| `WEB_ORIGIN` | The website origin the browser should land on, for example `https://srv2089893.hstgr.cloud`. The first origin is used. |

`SSLCZ_BASE_URL` is the only switch between sandbox and live. A blank value leaves the gateway off. Any other value is an allowlist: `staging` accepts only `sandbox.sslcommerz.com`, and `prod` accepts only `securepay.sslcommerz.com`. Anything else stops startup. `WEB_ORIGIN` is required on both of those profiles.

The API listens on `127.0.0.1`. Nginx publishes the callback URLs.

## URLs this API sends

`{callback}` is `SSLCZ_CALLBACK_BASE_URL`. `{tranId}` is the id stored on the order before the session request.

| Role | URL |
|---|---|
| Success | `{callback}/api/v1/live-exam-orders/{tranId}/return?status=success` |
| Fail | `{callback}/api/v1/live-exam-orders/{tranId}/return?status=fail` |
| Cancel | `{callback}/api/v1/live-exam-orders/{tranId}/return?status=cancel` |
| IPN | `{callback}/api/v1/live-exam-orders/{tranId}/ipn` |

Success, fail, and cancel accept GET and POST and do not use a login. The IPN is a form-encoded POST, also with no login. CSRF is disabled for the API. The posted `status` is ignored. Access is written only after the validation API returns `VALID` or `VALIDATED` and the transaction id, amount, currency, and store id match.

The browser is then sent to `{WEB_ORIGIN}/purchase/result?tranId={tranId}`. That page calls `GET /api/v1/live-exam-orders/status?tranId=` and shows the `status` and `examName` from the response. Only the signed-in owner can read it. The page polls every 3 seconds for up to 60 seconds while the status is `PENDING`. It does not grant access.

There is no fixed IPN path. The merchant panel must not replace these per-payment URLs with one panel URL.

## A good payment

1. A signed-in student posts `POST /api/v1/live-exam-orders` with `moduleId` or `examId`. The amount is the price stored on that row. The response includes `tranId` and `redirectUrl`.
2. The student pays on the sandbox page with a sandbox card. Do not use a real card.
3. SSLCommerz posts the IPN, or the browser posts the return. The API calls `validator/api/validationserverAPI.php` with the `val_id`.

Logs for that order, and nothing else about the gateway body:

- `sslcommerz session accepted tranId=<tranId>`
- `sslcommerz validation accepted tranId=<tranId>`
- `live exam payment granted tranId=<tranId>`
- `request completed method=POST path=/api/v1/live-exam-orders/<tranId>/ipn status=200`

A second IPN logs `live exam payment already granted tranId=<tranId>` and does not write a second grant.

The logs do not include the store id, the store password, a card number, or the raw SSLCommerz body. The validation call puts the store password in the query string because that is how SSLCommerz requires it. Do not turn on HTTP client debug logging.

## If the IPN never arrives

A job runs every 10 minutes. It asks `validator/api/merchantTransIDvalidationAPI.php` for `INITIATED` orders created between 10 minutes and 24 hours ago. A paid match is granted only after the same validation call. Five finished queries that find nothing stop further queries for that transaction until the process restarts. An unreachable query does not use one of the five. The counter is in memory.

## Unhappy-path checklist

- [ ] Fail the sandbox payment. The return redirects to `/purchase/result` and the order is not `SUCCEEDED`. No grant row.
- [ ] Cancel on the sandbox page. The posted `status=cancel` does not by itself cancel or grant. The order stays `INITIATED` until the query reports a cancel, the student cancels it, or a later validation succeeds.
- [ ] Send the same IPN twice. One grant, and the second response is still 200.
- [ ] Validation amount differs from the stored price. The order becomes `FAILED`. No grant.
- [ ] Validation currency is not `BDT`. The order becomes `FAILED`. No grant.
- [ ] Validation `store_id` is not the configured sandbox store. The order becomes `FAILED`. No grant.
- [ ] Stop the IPN from arriving, leave the order for at least 10 minutes, and confirm a later query grants it. `sslcommerz query paid` is followed by `sslcommerz validation accepted` and `live exam payment granted`.
- [ ] Start the API with `SPRING_PROFILES_ACTIVE=prod` and `SSLCZ_BASE_URL=https://sandbox.sslcommerz.com`. It must exit before serving. Do not leave that process running.
- [ ] Start it with `SPRING_PROFILES_ACTIVE=staging` and `SSLCZ_BASE_URL=https://securepay.sslcommerz.com`. It must exit before serving.
- [ ] Start either profile with any other `SSLCZ_BASE_URL` host. It must exit before serving.
- [ ] Start either profile with `WEB_ORIGIN` unset or blank. It must exit before serving.

## Staging seed

After dropping the staging database, start the API with the staging profile and `SEED_ENABLED=true`. It creates one system admin, one student, and one published exam priced at 10.00 BDT titled "Staging practice exam". Leave `SEED_ENABLED` unset on a normal start. The prod profile refuses to start when `SEED_ENABLED=true`.

The passwords are read from the environment. They are not in the source file.

- `SEED_ADMIN_PHONE`, `SEED_ADMIN_EMAIL`, `SEED_ADMIN_NAME`, `SEED_ADMIN_PASSWORD`
- `SEED_STUDENT_PHONE`, `SEED_STUDENT_EMAIL`, `SEED_STUDENT_NAME`, `SEED_STUDENT_PASSWORD`

Phones are Bangladesh numbers (`01XXXXXXXXX` or `+8801XXXXXXXXX`). Passwords are 8 to 72 characters. Starting again with the same emails and exam title does not create a second copy.

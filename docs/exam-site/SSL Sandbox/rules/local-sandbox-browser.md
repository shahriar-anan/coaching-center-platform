# Local sandbox browser test

Read this before implementing `../plans/local-sandbox-browser.md`.

## In scope

One seed student, on this PC, buys one 10.00 BDT exam from `/sandbox` on the Next dev server. The API runs on the dev profile against `https://sandbox.sslcommerz.com`. The existing `/purchase/result` page shows what the status endpoint returns.

## Out of scope

- The new server, the staging and prod profiles, and `https://securepay.sslcommerz.com`.
- The live store. It never goes in `api/.env`.
- Book orders, course purchases, payment verification, and offline admission.
- A student catalog, a sitting, or a designed exam page. `/sandbox` is a test page.
- Dropping any database.
- JUnit or component tests that call SSLCommerz.
- An IPN delivered to this PC, and refunds.
- HTTP client debug or wire logging.
- A store id, store password, card number, `val_id`, or seed password in any committed file.

## Hosts

Website `http://localhost:3000`. API `http://localhost:8080`. Never `127.0.0.1` or a LAN address in the browser. The refresh cookie is `Secure` and `SameSite=Strict` on path `/api/v1/auth`. Browsers keep a Secure cookie on `localhost` and drop it on plain `http://127.0.0.1`.

The SSLCommerz redirect back to this PC is a cross-site navigation, so it does not carry that cookie. The result page's own call to `/api/v1/auth/refresh` is same-site and does carry it. If the cookie is missing, the result page's sign-in form is the fallback.

The dev profile loads `api/.env` only when the API process starts in `api/`. Tests started in `api/` load it too. The test base class forces the seed off and blanks `SSLCZ_*`.

## Payment rules the new code must keep

- The page sends `examId` only. Amount, currency, and `tran_id` come from the API.
- Price 10.00. A price of 0 is rejected by the database.
- Grant only after validation matches transaction id, amount, currency, and store id.
- The posted `status` on a return is never trusted. A missing `val_id` may be resolved only by SSLCommerz's own query API, and a paid query result still goes through validation.
- `/purchase/result` displays. It does not grant, and it does not turn Pending into Failed when the minute ends.
- An open order is reused. A rejected session must still return its `orderId` so the student can cancel.
- Cancel never grants. A verified success after a cancel still grants.
- The admin `/login` still rejects a student. `/sandbox` and `/purchase/result` keep a student signed in. Admin pages do not.

## Data

Nothing is created until slice 6. The seed runs on dev or staging only with `SEED_ENABLED=true`. Prod refuses that flag.

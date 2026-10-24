# Local SSLCommerz sandbox

Goal: Shahriar pays for one exam in a browser on this PC, against `https://sandbox.sslcommerz.com`, through the real API and website code.

Not the new server, not the prod profile, not `https://securepay.sslcommerz.com`. Do not drop a database. Do not put a store id, store password, or card number in a file that git tracks.

## Read in this order

1. `rules/local-sandbox-browser.md`
2. `plans/local-sandbox-browser.md`
3. `tests/local-sandbox-browser.md`
4. `../reports/sslcommerz-sandbox-runbook.md` for what the API already does. Where it names the server hostname, this folder wins for the local test.

## Task list

Do not start a slice until the one before it exits. Do not create the student or the exam until slice 6.

| | Slice | Exit |
|---|---|---|
| 1 | Check the gateway before inserting an order. A rejected session returns `503` with `orderId` and `tranId`. Trim the four SSLCommerz settings. Add the names to `api/.env.example`. | `LiveExamPurchaseIT` and a properties unit test |
| 2 | A return without `val_id` asks SSLCommerz's query API once, so fail and cancel can show on the result page. | `LiveExamPurchaseIT` |
| 3 | `/sandbox`: student sign-in, priced exams, Buy, and Cancel for the stored pending order. | `npm test`, `npm run lint`, `npm run build` |
| 4 | The seed may run on dev when `SEED_ENABLED=true`. The test suite forces it off. | `StagingExamSeedTest` |
| 5 | Full API suite and web checks. | No payment-path failure |
| 6 | Seed one admin, one student, one 10.00 BDT exam in `coaching_center_dev`. | Exam listed on `/sandbox` |
| 7 | Shahriar's browser checklist. | Result row filled in |

## The env file

Edit `api/.env`. It is gitignored. It is the only env file for this test. The website has none; with `NEXT_PUBLIC_API_BASE_URL` unset it calls `http://localhost:8080`.

Your `api/.env` does not have the SSLCommerz names yet. Add these:

```
SSLCZ_STORE_ID=
SSLCZ_STORE_PASSWORD=
SSLCZ_BASE_URL=https://sandbox.sslcommerz.com
SSLCZ_CALLBACK_BASE_URL=http://localhost:8080
```

Put the sandbox store id and password after the `=`. Use the sandbox pair that returned store name Demo. Keep `WEB_ORIGIN=http://localhost:3000`.

The file is read as Java `.properties`:

- No quotes. A quote becomes part of the value.
- No space before or after a value. Slice 1 trims, but do not rely on it before slice 1 lands.
- A `\` in a value must be written `\\`.
- Restart the API after every change.

Add these for slice 6, with `SEED_ENABLED=false` until then:

```
SEED_ENABLED=false
SEED_ADMIN_PHONE=
SEED_ADMIN_EMAIL=
SEED_ADMIN_NAME=
SEED_ADMIN_PASSWORD=
SEED_STUDENT_PHONE=
SEED_STUDENT_EMAIL=
SEED_STUDENT_NAME=
SEED_STUDENT_PASSWORD=
```

Phones are `01XXXXXXXXX` or `+8801XXXXXXXXX`, different from each other and from `MASTER_ADMIN_PHONE`. Emails must not already exist in `coaching_center_dev`. Passwords are 8 to 72 characters.

## Starting

```powershell
cd f:\coaching-center-platform\api
mvn spring-boot:run
```

```powershell
cd f:\coaching-center-platform\web
npm run dev
```

Open `http://localhost:3000/sandbox`.

## If the session is rejected

The sandbox might refuse `http://localhost:8080` as a return address. Slice 1 makes that visible and cancellable. It does not make the payment possible.

The fix is a public `https` address that forwards to this PC's port 8080, such as a temporary tunnel, set as `SSLCZ_CALLBACK_BASE_URL`. Keep `WEB_ORIGIN=http://localhost:3000`. That would also let the sandbox deliver an IPN. It is not part of this plan. It needs Shahriar's decision before it is set up.

## Known limits

- No IPN reaches `localhost`. A grant on the browser return is a pass.
- The 10-minute recheck runs on dev while the gateway is configured. It may mark an old pending order during the pass.
- If two orders for the same exam are both paid, the second is marked paid with no second grant. There is no refund flow.
- Chrome may later block a public page from sending the browser to `localhost`. If the return never reaches `localhost:8080`, try Edge or Firefox before changing code, and record it.

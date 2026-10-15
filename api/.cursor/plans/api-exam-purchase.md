# api-exam-purchase

Stage plan for Phase 3 Stage 5. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Section 7 and Stage 5, reusing Phase 2 Section 6. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-purchase.md](../tests/api-exam-purchase.md). The stage rule is [api-exam-purchase.mdc](../rules/api-exam-purchase.mdc).

Start only after `api-exam-access` exits. Do not start `api-exam-mcq` until this stage exits.

Extend the existing payment record. Do not add a second verifier, a gateway, a refund, a cancel route, or an expiry job.

Student permission `PURCHASE_CREATE` already exists. Verification stays `PAYMENT_VERIFY`, Master Admin only.

When the slice's checks finish, write `api/.cursor/progress/api-exam-purchase/slice-1-purchase-and-verify.md`.

## Slice 1 — Purchase and verify

Flyway `V14__exam_module_purchases.sql`.

`exam_module_purchases`: student id, module id, `status` (`AWAITING_PAYMENT`, `ACTIVE`, `PAYMENT_FAILED`), `price_paid` numeric, `started_at`, and the base-entity columns. No `expires_at` and no `opening_number`.

Replace `payments_payable_type_check` so `payable_type` allows `COURSE_ENROLLMENT`, `BOOK_ORDER`, and `EXAM_MODULE`. `payable_id` for `EXAM_MODULE` is the `exam_module_purchases` id.

`POST /api/v1/exam-modules/{id}/purchases` with `method` (`BKASH` or `NAGAD`) and `transactionRef`, under `PURCHASE_CREATE`.

- The module must be published and `PURCHASE_REQUIRED`. Otherwise `409`, code `EXAM_NOT_FOR_SALE`, and no row is written. `OPEN` and `ENROLLED_ONLY` use that same code.
- Create the purchase as `AWAITING_PAYMENT` with `price_paid` copied from the module price, and a `PENDING` payment. `accessGranted` stays false.
- The response includes the coaching-center number for the chosen method, from the existing payment-number settings.
- A duplicate `transaction_ref` returns `409`, code `DUPLICATE_TRANSACTION_REF`.
- A second pending purchase is allowed while the first is still `AWAITING_PAYMENT`. Once an `ACTIVE` purchase exists for that student and module, a further purchase returns `409`, code `ALREADY_PURCHASED`.
- `GET /api/v1/me/exam-module-purchases` returns only the caller's rows. Do not change the course shape of `GET /api/v1/me/purchases`.

`POST /api/v1/payments/{id}/verify` gains a branch for `EXAM_MODULE`. Keep the course and book branches.

- Success sets the purchase `ACTIVE` and the payment `SUCCESS`, and audits `PAYMENT_SUCCESS`, only when the module is still published and `PURCHASE_REQUIRED`. Otherwise the payment stays `PENDING`, nothing is activated, and the body uses `EXAM_NOT_FOR_SALE`.
- Failure sets the payment `FAILED` and the purchase `PAYMENT_FAILED`, audits `PAYMENT_FAILED`, and grants nothing. `accessGranted` stays false. A second verify returns `409`, code `PAYMENT_NOT_PENDING`.
- `price_paid` is copied when the purchase starts. A later module price change does not rewrite that column.
- `GET /api/v1/payments` accepts `payableType=EXAM_MODULE`. An `EXAM_MODULE` row includes the module's two titles. Course and book rows stay as they are.
- After success, the Stage 4 student detail reports `accessGranted` true for that student. Set `started_at` far in the past in the test database and assert access is still true. There is no expiry column to advance.

No route cancels a purchase or a payment.

## Stage exit

A student can purchase a standalone module through the existing manual-verification flow and gains access once an admin confirms the payment. That access does not expire.

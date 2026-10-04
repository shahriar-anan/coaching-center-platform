# api-payment-access tests

Required cases for Phase 2 Stage 5. Written during implementation. PostgreSQL only. No manual check.

## PaymentVerifyIT

Slice 1.

- A System Admin verify returns `403`. A Master Admin can list pending payments.
- Success on an open course sets the enrollment `ACTIVE` and the payment `SUCCESS`, and audits `PAYMENT_SUCCESS`.
- If the course price changed while the payment was pending, `price_paid` stays the original amount.
- Success after the Master Admin has closed the course leaves the payment pending, returns `COURSE_NOT_OPEN`, and does not set the enrollment `ACTIVE`.
- Failure sets the payment `FAILED` and the enrollment `PAYMENT_FAILED`, and audits `PAYMENT_FAILED`.
- Success on an online book order sets the order `PAID`. Failure sets the order `PAYMENT_FAILED` and not `PAID`.
- Verifying a payment that is already success or failed returns `PAYMENT_NOT_PENDING`.
- `status=SUCCESS` with `payableType=COURSE_ENROLLMENT` returns that course payment and not a book payment. `status=FAILED` with `payableType=BOOK_ORDER` returns that book payment. `status=CANCELLED` and an unknown `payableType` return `400`. A System Admin list returns `403`.

## OfflineAdmissionIT

Slice 2.

- A System Admin and a Master Admin can each admit a student to an open freemium course. The enrollment is `ACTIVE`, `source` `OFFLINE`, current `opening_number`, with no payment.
- Admitting into a closed course returns `COURSE_NOT_OPEN`.
- Admitting into a full-free course returns `COURSE_IS_FREE`.
- A second admission for the same student and opening returns `ALREADY_ENROLLED`.
- After close and reopen, a new admission succeeds, and the old enrollment's `opening_number` is unchanged.

## OrderFulfillmentIT

Slice 3.

- An online delivery order can move `PAID` → `READY` → `DELIVERED` by a System Admin.
- An online pickup order can move to `COLLECTED`. Using `DELIVERED` on a pickup order returns `FULFILLMENT_MISMATCH`.
- A System Admin can set a cash order to `READY` and receives `403` on `COLLECTED`.
- A Master Admin `COLLECTED` on that cash order audits `CASH_RECEIVED`.
- No order status is `CANCELLED`, and no cancel route is in the OpenAPI document.

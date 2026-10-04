# api-purchase

Stage plan for pending purchases and book orders. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Sections 5, 6, and 7.2–7.4, and Stage 4. This plan adds order only. It does not drop an exit check.

Required cases are in [api-purchase.md](../tests/api-purchase.md).

Start only after `api-catalog-browse` exits. Do not start `api-payment-access` until this stage exits. This stage does not activate an enrollment or advance an order to paid.

Do not add a cancel route, a refund route, a refunded status, or a payment gateway. Verification is the next stage.

Package: `com.coachingcenter.api.purchases`. One payment table for both course enrollments and book orders.

When a slice's checks finish, write `api/.cursor/progress/api-purchase/slice-<number>-<name>.md` before the next slice.

## Shared records

Flyway `V6__purchases.sql`.

`enrollments`: student id, course id, `opening_number`, `delivery_mode`, `source` (`ONLINE` for this stage), `status` (`AWAITING_PAYMENT`; `ACTIVE` and `PAYMENT_FAILED` are written by the next stage), `price_paid` numeric, `started_at`. No expiry column. No cancelled status.

`book_orders`: student id, book id, `fulfillment` (`PICKUP` or `DELIVERY`), `payment_method` (`ONLINE` or `CASH`), `status` (`PLACED` for this stage), `phone`, `address` nullable, `area` nullable.

`payments`: student id, `payable_type` (`COURSE_ENROLLMENT` or `BOOK_ORDER`), `payable_id`, `amount`, `method` (`BKASH` or `NAGAD`), `transaction_ref`, `status` (`PENDING`), timestamps. Unique `transaction_ref`. A duplicate returns `409`, code `DUPLICATE_TRANSACTION_REF`. Cash orders have no payment row.

The coaching center numbers come from `PAYMENT_BKASH_NUMBER` and `PAYMENT_NAGAD_NUMBER`. Do not commit them. The purchase response includes the number for the method the student chose.

Permission `PURCHASE_CREATE` is granted to `STUDENT`.

## Slice 1 — Course purchase

`POST /api/v1/courses/{id}/purchases` with `method` and `transactionRef`. If the course is `HYBRID`, the body also includes `deliveryMode`. A fixed online or offline course stores that course's mode.

- The course must be published and open. Otherwise `409`, code `COURSE_NOT_OPEN`.
- A full-free course returns `409`, code `COURSE_IS_FREE`.
- If this student already has an `ACTIVE` enrollment for this course and this `opening_number`, return `409`, code `ALREADY_ENROLLED`. Awaiting-payment does not count as already enrolled.
- Create the enrollment as `AWAITING_PAYMENT` with `price_paid` copied from the course price at this moment, and a `PENDING` payment. Access is not granted.
- `GET /api/v1/me/purchases` returns only the caller's rows and their payment status.

## Slice 2 — Book order

`POST /api/v1/books/{id}/orders`.

- Unavailable or missing books return `409`, code `BOOK_UNAVAILABLE`.
- `phone` is always required.
- `DELIVERY` requires `address`. `area` is optional. Missing address returns `400`, code `ADDRESS_REQUIRED`.
- `CASH` requires `PICKUP`. Cash plus delivery returns `400`, code `CASH_REQUIRES_PICKUP`. Cash creates an order in `PLACED` and no payment.
- `ONLINE` requires `method` and `transactionRef`, creates a `PENDING` payment, and leaves the order `PLACED`.
- `GET /api/v1/me/orders` returns only the caller's orders.
- No route cancels an enrollment, a payment, or an order. A test asserts those routes are absent.

## Stage exit

A student can start a course purchase or a book order through either payment method, including a delivery address when delivery is chosen, and can see the pending status. A second purchase of a course they already hold for this opening is refused.

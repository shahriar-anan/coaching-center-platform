# api-purchase tests

Required cases for Phase 2 Stage 4. Written during implementation. PostgreSQL only. No manual check.

Payment numbers come from the test environment. Do not assert a committed secret.

## CoursePurchaseIT

Slice 1.

- Purchasing a published open freemium course creates an `AWAITING_PAYMENT` enrollment and a `PENDING` payment. The enrollment stores the price at that moment, the current `opening_number`, and `source` `ONLINE`. The response includes the payment number for the chosen method.
- The student can see that row on `GET /me/purchases` and cannot see another student's row.
- A hybrid course requires `deliveryMode`. An online-only course stores `ONLINE`.
- A closed, draft, or archived course returns `COURSE_NOT_OPEN` and creates nothing.
- A full-free course returns `COURSE_IS_FREE`.
- After an enrollment is `ACTIVE` for this opening, a second purchase returns `ALREADY_ENROLLED`.
- A second pending purchase is allowed while the first is still `AWAITING_PAYMENT`. The duplicate `transaction_ref` still returns `DUPLICATE_TRANSACTION_REF`.
- The response does not include an access token for content, and the paid lecture route from a later stage is not required here. Assert the enrollment is not `ACTIVE`.

## BookOrderIT

Slice 2.

- Delivery with phone and address creates a `PLACED` online order and a `PENDING` payment. `area` may be omitted.
- Delivery without address returns `ADDRESS_REQUIRED`.
- Cash with pickup creates a `PLACED` order and no payment row.
- Cash with delivery returns `CASH_REQUIRES_PICKUP`.
- An unavailable book returns `BOOK_UNAVAILABLE`.
- `GET /me/orders` returns only the caller's orders.
- `POST` or `DELETE` routes for cancelling a purchase, a payment, or an order are not in the generated OpenAPI document.

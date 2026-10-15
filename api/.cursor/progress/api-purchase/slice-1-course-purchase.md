# api-purchase — slice 1 (course purchase)

## Done

- V6 `enrollments`, `book_orders`, `payments`; `PurchaseService` / `PurchaseController` for course purchase and `GET /me/purchases`.
- `PURCHASE_CREATE` (student, own scope); payment numbers from `PaymentProperties` / test env.
- Rules: open published freemium only; hybrid `deliveryMode`; duplicate `transaction_ref`; `ALREADY_ENROLLED` when ACTIVE for opening; multiple `AWAITING_PAYMENT` allowed.

## Tested

- `CoursePurchaseIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Open

Re-run after the course-depth follow-on returned `409`. Payment `trx-open-1` is already pending in `coaching_center_test`, so that purchase was not re-checked.

## Next

- Slice 2 (`BookOrderIT`).

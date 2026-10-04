# api-purchase — slice 1 (course purchase)

## Done

- V6 `enrollments`, `book_orders`, `payments`; `PurchaseService` / `PurchaseController` for course purchase and `GET /me/purchases`.
- `PURCHASE_CREATE` (student, own scope); payment numbers from `PaymentProperties` / test env.
- Rules: open published freemium only; hybrid `deliveryMode`; duplicate `transaction_ref`; `ALREADY_ENROLLED` when ACTIVE for opening; multiple `AWAITING_PAYMENT` allowed.

## Tested

- `CoursePurchaseIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 2 (`BookOrderIT`).

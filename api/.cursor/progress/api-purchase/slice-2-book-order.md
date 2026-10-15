# api-purchase — slice 2 (book order)

## Done

- `POST /api/v1/books/{id}/orders` and `GET /api/v1/me/orders`; delivery address required for delivery; cash pickup only (no payment row); online orders create `PENDING` payment.

## Tested

- `BookOrderIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Open

Re-run after the course-depth follow-on returned `409`. Payment `book-trx-1` is already pending in `coaching_center_test`, so that order was not re-checked.

## Stage exit

Met.

## Next

- `api-payment-access` slice 1 (`PaymentVerifyIT`).

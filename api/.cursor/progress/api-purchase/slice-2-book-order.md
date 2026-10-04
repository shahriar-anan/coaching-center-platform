# api-purchase — slice 2 (book order)

## Done

- `POST /api/v1/books/{id}/orders` and `GET /api/v1/me/orders`; delivery address required for delivery; cash pickup only (no payment row); online orders create `PENDING` payment.

## Tested

- `BookOrderIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Stage exit

Met.

## Next

- `api-payment-access` slice 1 (`PaymentVerifyIT`).

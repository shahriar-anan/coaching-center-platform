# api-payment-access — slice 3 (fulfillment and cash)

## Done

- `ORDER_FULFILL` / `CASH_RECEIVE` permissions; `PATCH /api/v1/book-orders/{id}` for status transitions.
- Online `PAID` → `READY` → `DELIVERED`/`COLLECTED` with `FULFILLMENT_MISMATCH` guard; cash `PLACED` → `READY` → `COLLECTED` (Master Admin only) with `CASH_RECEIVED` audit.

## Tested

- `OrderFulfillmentIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Stage exit

Met.

## Next

- `api-content-access` slice 1 (`ContentAccessIT`).

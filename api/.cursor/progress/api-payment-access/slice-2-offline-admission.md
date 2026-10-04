# api-payment-access — slice 2 (offline admission)

## Done

- `ENROLLMENT_GRANT` for both admin roles; `POST /api/v1/courses/{id}/admissions` creates `ACTIVE` offline enrollments with no payment.

## Tested

- `OfflineAdmissionIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 3 (`OrderFulfillmentIT`).

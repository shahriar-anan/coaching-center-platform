# api-payment-access — slice 1 (verify payment)

## Done

- `PAYMENT_VERIFY` (Master Admin only); `GET /api/v1/payments?status=PENDING` and `POST /api/v1/payments/{id}/verify`.
- Course success activates enrollment when course is open and opening matches; closed course leaves payment pending (`COURSE_NOT_OPEN`).
- Book success sets order `PAID`; failure paths set `PAYMENT_FAILED`; audits `PAYMENT_SUCCESS` / `PAYMENT_FAILED`.

## Tested

- `PaymentVerifyIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 2 (`OfflineAdmissionIT`).

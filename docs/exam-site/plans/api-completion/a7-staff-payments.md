# A7 — Staff view of exam payments

Written 25 October 2026, after the VPS staging deploy. Every live exam order call is student-only, so the Master Admin cannot list payments, look up a `tranId`, or answer "I paid but I have no access" without the database or the SSLCommerz merchant panel. Shahriar approved the recommended approach on 25 October 2026. [README.md](README.md) practices apply.

## Decisions

| # | Question | Choice |
|---|---|---|
| P1 | Who reads it | Master Admin only, through a new permission `LIVE_EXAM_ORDER_READ`. Same audience as `PAYMENT_VERIFY` and the roster, which is the manual fix when a paid order has no access. System Admin, instructor and student get `403`. |
| P2 | Writes | None. Read only, so no audit row. Refunds stay in the merchant panel. |
| P3 | Payment method | Flyway `V37` adds `live_exam_orders.payment_channel VARCHAR(64) NULL`. Filled from the SSLCommerz validation reply (`card_type`, else `card_brand`, for example `BKASH-BKash` or `VISA-Dutch Bangla`) in the transaction that marks the order `SUCCEEDED`. Trimmed to 64 printable characters. Orders before `V37` stay null. The card number is never stored. Students do not see this field. |
| P4 | Access check | `accessGranted` is true when a `live_exam_grants` row has this `order_id`. Roster access is not counted, because it does not come from the order. |
| P5 | Student contact | The row shows the student's name, phone, email and student code. Staff already read these on `/api/v1/students`, and support needs them. |

## Endpoint

`GET /api/v1/live-exam-orders?status=&tranId=&q=&page=&size=`

- `status`: `INITIATED`, `SUCCEEDED`, `FAILED` or `CANCELLED`. Anything else is `400` `VALIDATION_FAILED`.
- `tranId`: exact match.
- `q`: contains-match on the student's phone, email, name or student code, case-insensitive.
- Newest first. `size` capped at 100. Returns `PageResponse`.

Row: `orderId`, `tranId`, `status`, `amount`, `currency`, `scope`, `moduleId`, `moduleTitleEn`, `moduleTitleBn`, `examId`, `examTitleEn`, `examTitleBn`, `paymentChannel`, `valId`, `accessGranted`, `studentId`, `studentName`, `studentPhone`, `studentEmail`, `studentCode`, `createdAt`, `updatedAt`.

## Tests

`LiveExamPurchaseIT`:

- The Master Admin lists a succeeded order with the channel from validation and `accessGranted` true, and a failed order with a null channel and `accessGranted` false. The DB row holds the channel.
- Filters: `status`, `tranId`, and `q` by phone and by name. Paging and newest first.
- An unknown `status` is `400`.
- System Admin, instructor and student `403`. No token `401`.

`SslCommerzGatewayTest`: validation reads `card_type`, falls back to `card_brand`, and trims a long value.

`AuditLogIT` lists the new route. [../../current-api.md](../../current-api.md) gains the call.

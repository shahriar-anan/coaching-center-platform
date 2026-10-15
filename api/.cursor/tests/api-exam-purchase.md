# api-exam-purchase tests

Required cases for Phase 3 Stage 5. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Payment numbers come from the test environment. Do not assert a committed secret.

Shared setup: a student, a Master Admin, a published `PURCHASE_REQUIRED` module with a price, and a published `OPEN` module.

## ExamPurchaseIT

Slice 1.

- Purchasing the `PURCHASE_REQUIRED` module creates an `AWAITING_PAYMENT` purchase and a `PENDING` payment with `payable_type` `EXAM_MODULE`. `price_paid` is the module price at that moment. The response includes the payment number for the chosen method. Catalog `accessGranted` stays false.
- The student can see that row on `GET /me/exam-module-purchases` and cannot see another student's row. `GET /me/purchases` does not change its course-enrollment shape.
- `OPEN` and `ENROLLED_ONLY` return `EXAM_NOT_FOR_SALE` and create nothing. A draft or archived `PURCHASE_REQUIRED` module does the same.
- A duplicate `transaction_ref` returns `DUPLICATE_TRANSACTION_REF`. A second pending purchase with a new reference is allowed. After the first payment is verified successfully, another purchase returns `ALREADY_PURCHASED`.
- A System Admin verify returns `403`. A Master Admin success sets the purchase `ACTIVE`, the payment `SUCCESS`, audits `PAYMENT_SUCCESS`, and the student's catalog detail then has `accessGranted` true.
- Success when the module is no longer published, or is no longer `PURCHASE_REQUIRED`, leaves the payment `PENDING` and returns `EXAM_NOT_FOR_SALE`.
- A failed verify sets the purchase `PAYMENT_FAILED`, grants nothing, and audits `PAYMENT_FAILED`. A second verify returns `PAYMENT_NOT_PENDING`. `accessGranted` stays false.
- Changing the module price after the purchase starts does not change `price_paid` on that row.
- The `exam_module_purchases` table has no `expires_at` column. Setting `started_at` a year in the past leaves `accessGranted` true.
- `GET /payments?payableType=EXAM_MODULE` returns the module's two titles. `POST` or `DELETE` routes for cancelling the purchase are not in the generated OpenAPI document.

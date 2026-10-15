# Slice 1 — Purchase and verify

## Done
- Flyway `V19__exam_module_purchases.sql` and `EXAM_MODULE` payable type.
- `ExamModulePurchaseService`, purchase/verify integration in `PaymentAccessService`.
- `POST /exam-modules/{id}/purchases`, `GET /me/exam-module-purchases`.
- `ExamAccessService` grants access on `ACTIVE` purchase.
- `ExamPurchaseIT`.

## Tested
- `mvn test -Dtest=ExamPurchaseIT` — **passed**.

## Next
- `api-exam-mcq`.

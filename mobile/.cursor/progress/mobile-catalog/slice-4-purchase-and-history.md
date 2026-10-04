# mobile-catalog — slice 4 (purchase and history)

## Done

- `AuthApi.purchaseCourse` and `listMyPurchases`; models `CoursePurchaseResult`, `CoursePurchaseView`.
- `CoursePurchaseScreen`: bKash/Nagad, transaction ref, delivery mode when course is hybrid; payment number after submit; history from `GET /me/purchases`; no cancel control.
- Freemium course detail shows `Key('course-purchase')`; full-free has no purchase button.
- i18n EN/BN including `ALREADY_ENROLLED` and `COURSE_IS_FREE`.
- Tests: `course_purchase_test.dart`; course detail test expects purchase on freemium.

## Verify

```bash
cd mobile && dart analyze && flutter test
```

## Next

- Slice 5: library book order.

# mobile-catalog — slice 5 (library order)

## Done

- `AuthApi.getBook`, `placeBookOrder`, `listMyOrders`; models `BookDetail`, `BookOrderResult`, `BookOrderView`; `coverFileId` on book summaries.
- `BookDetailScreen` (cover via files API, metadata, order entry).
- `BookOrderScreen`: pickup/delivery, online/cash with cash+delivery blocked in UI; phone; address/area for delivery; online payment fields; order history; no cancel control.
- Library tab navigates to book detail.
- Tests: `book_detail_test.dart`, `book_order_test.dart`.

## Verify

```bash
cd mobile && dart analyze && flutter test
```

## Next

- Phone exit: Shahriar manual QA → `phone-exit.md`.

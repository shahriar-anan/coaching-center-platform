# web-catalog — slice 5 (payments and fulfillment)

## Done

- API `GET /api/v1/book-orders` with `OrderFulfillmentIT` coverage.
- Pending payments page (Master Admin nav only).
- Book orders fulfillment actions; cash collected Master Admin only.
- Offline admission on open freemium course detail.

## Verify

`npm run lint`, `npm run build`. API: `CourseTreeIT`, `OrderFulfillmentIT` when PostgreSQL is up.

## Next

Shahriar browser exit → `browser-exit.md`.

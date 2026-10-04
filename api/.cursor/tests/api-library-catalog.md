# api-library-catalog tests

Required cases for Phase 2 Stage 2. Written during implementation as `BookCatalogIT`. PostgreSQL only, same rules as the course catalog tests. No manual check.

## BookCatalogIT

- Create stores `title_en` and `title_bn`. Author and description are single fields.
- There is no stock column.
- A System Admin cannot set `price` (`403`). A Master Admin can.
- Marking a book available with no price returns `PRICE_REQUIRED`. After a price is set, the available toggle can turn on and off.
- Cover upload uses the local file store and the same size limit as course materials.
- List hides soft-deleted books and uses the page envelope.
- A System Admin delete returns `403`. A Master Admin delete audits `BOOK_DELETE`.

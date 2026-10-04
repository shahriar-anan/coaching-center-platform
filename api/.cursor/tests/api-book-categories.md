# api-book-categories tests

Follow-on cases. Not part of the `api-library-catalog` stage exit. The class is `BookCategoryIT` against PostgreSQL. Do not use H2. Do not add these assertions to `BookCatalogIT`.

The Master Admin creates every book in this class. A System Admin is used only where the case says so.

## BookCategoryIT

- A Master Admin creates Category A, then Category B. `GET /book-categories` returns that order as a flat list with positions `0` and `1`. The payload has no `children`, no `parentId`, and no `kind`.
- Book-1 placed on Category A and Book-2 placed on Category B each read back with that category's id. Moving Book-1 onto Category B replaces the previous category. Book-1 then has only Category B's id.
- `categoryId` null clears placement. A soft-deleted book no longer keeps its category occupied, and that category can then be deleted.
- A System Admin can create a category and place a book, and cannot delete a category (`403`). A Master Admin delete of a category that still has a book returns `409`, code `CATEGORY_NOT_EMPTY`. A Master Admin delete of an empty category is a soft delete, audits `BOOK_CATEGORY_DELETE` with actor and entity id, and the category is absent from the list.
- A student `GET` returns `403`. A missing token returns `401`. Missing titles return `400`, code `VALIDATION_FAILED`. A sibling position already used returns `409`, code `CATEGORY_POSITION_TAKEN`. An unknown category id returns `404`. Sending `parentId` returns `400`, code `VALIDATION_FAILED`.
- Creating a book category inserts no `content_nodes` row and no `course_categories` row. `GET /api/v1/catalog/books` is unchanged: it does not require a category and does not accept one as a filter.

# api-book-categories

Follow-on to [api-library-catalog.md](api-library-catalog.md). Not part of that stage's exit. A book category is one flat label. It is not a course category, and it has no parent. One book has at most one category.

Required cases are in [api-book-categories.md](../tests/api-book-categories.md). The rule is [api-book-categories.mdc](../rules/api-book-categories.mdc).

Packages stay under `com.coachingcenter.api.library`. Reuse `BOOK_READ`, `BOOK_MANAGE`, and `BOOK_DELETE`. Do not add a permission. Only `MASTER_ADMIN` can delete.

When `BookCategoryIT` passes, write `api/.cursor/progress/api-library-catalog/slice-2-book-categories.md`.

Do not change `GET /api/v1/catalog/books` or `GET /api/v1/catalog/books/{id}`. Student browse does not filter or group by category, and the mobile follow-on does not add a book category. Price, availability, and cover rules on a book stay as they are.

## Storage

Flyway `V8__book_categories.sql`.

`book_categories`: `title_en`, `title_bn`, `position`, plus the base entity columns. There is no `parent_id`.

`books` gains nullable `category_id` referencing `book_categories`. There is no `category_position`. A book is not a category.

## API

- `GET /api/v1/book-categories` under `BOOK_READ` returns a flat list ordered by `position`. Each item has `id`, `titleEn`, `titleBn`, and `position`. The list does not contain books and has no `children`. Soft-deleted rows are absent.
- `POST /api/v1/book-categories` and `PATCH /api/v1/book-categories/{id}` under `BOOK_MANAGE`. Both titles are required on create. An omitted `position` appends after the current categories. A position already used returns `409`, code `CATEGORY_POSITION_TAKEN`. A `parentId` on create or update returns `400`, code `VALIDATION_FAILED`.
- `PATCH /api/v1/books/{id}` may set `categoryId` under `BOOK_MANAGE`. `categoryId` null clears the placement. A new `categoryId` replaces the previous one. The book read model includes `categoryId` when set.
- `DELETE /api/v1/book-categories/{id}` is a soft delete under `BOOK_DELETE`, audited as `BOOK_CATEGORY_DELETE` with entity type `BOOK_CATEGORY`. A category that still has a book returns `409`, code `CATEGORY_NOT_EMPTY`.

A missing category or book returns `404`, code `RESOURCE_NOT_FOUND`. A student or a missing token cannot read the list (`403` or `401`).

# api-library-catalog — slice 2 (book categories)

Follow-on. Not part of the library stage exit.

## Done

- Flyway `V8__book_categories.sql`. Flat `book_categories` list. `books.category_id` is nullable. No parent and no `category_position`.
- `GET`, `POST`, and `PATCH /api/v1/book-categories` for admins with book read or manage. `DELETE` is `BOOK_DELETE` only and audits `BOOK_CATEGORY_DELETE`.
- `PATCH /api/v1/books/{id}` sets or clears `categoryId`. The book read model includes `categoryId` when set.
- Student catalog book routes are unchanged.

## Tested

- `BookCategoryIT` — 3 tests passed against local PostgreSQL (`coaching_center_test`).

## Next

- `web-catalog` slice 7, after the mobile phone exit. Library view and details use this API.

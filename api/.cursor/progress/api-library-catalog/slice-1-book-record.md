# api-library-catalog — slice 1 (book record)

## Done

- Flyway `V5__books.sql`, `books` table, `materials.book_id` for cover storage.
- `com.coachingcenter.api.library`: book CRUD, permissions, cover upload, `BOOK_DELETE` audit.
- `GET /api/v1/files/{id}` allows `BOOK_READ` for book materials.

## Tested

- `BookCatalogIT` — passed (local PostgreSQL via `TEST_DB_*`).
- `CourseContentIT` — still passes after `materials.course_id` nullable.

## Stage exit

Met: a book can be created, edited, toggled available, and soft-deleted through the API; both titles are stored.

## Next

- `api-catalog-browse` slice 1 (`CatalogBrowseIT`).

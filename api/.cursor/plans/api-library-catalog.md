# api-library-catalog

Stage plan for Phase 2 books. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Section 7.1 and Stage 2. This plan adds order only. It does not drop an exit check.

Required cases are in [api-library-catalog.md](../tests/api-library-catalog.md).

Start only after `api-course-catalog` exits. Do not start `api-catalog-browse` until this stage exits.

Do not build student browse, orders, payments, delivery status, or web screens. Cover files use the `files` store from the previous stage. Do not add Cloudinary.

Package: `com.coachingcenter.api.library`. Register permissions from a contributor in that package. Only `MASTER_ADMIN` receives `BOOK_DELETE`.

When the slice's checks finish, write `api/.cursor/progress/api-library-catalog/slice-1-book-record.md` before the next stage.

## Permissions

| Permission | Master Admin | System Admin |
|---|---|---|
| `BOOK_READ` | yes | yes |
| `BOOK_MANAGE` | yes | yes |
| `BOOK_PRICE` | yes | no |
| `BOOK_DELETE` | yes | no |

`BOOK_MANAGE` edits both titles, author, description, cover, and the available flag. It does not set the price. A System Admin price change returns `403`.

## Slice 1 — Book record

Flyway `V5__books.sql`. Table `books` extending the base entity.

Columns: `title_en`, `title_bn`, `author`, `description`, `price` numeric nullable, `available` boolean, nullable `cover_file_id`. Author and description are one language each. There is no stock count.

- `POST /api/v1/books` and `PATCH /api/v1/books/{id}` under `BOOK_MANAGE`, price under `BOOK_PRICE`.
- A book cannot be marked available until a price is set (`409`, code `PRICE_REQUIRED`). The Master Admin sets the price.
- `GET /api/v1/books` and `GET /api/v1/books/{id}` under `BOOK_READ`, paged, soft-deleted rows absent.
- `POST /api/v1/books/{id}/cover` multipart under `BOOK_MANAGE`, same size limit and local store as course files.
- `DELETE /api/v1/books/{id}` soft-deletes under `BOOK_DELETE` and audits `BOOK_DELETE`.

## Stage exit

A book can be created, edited, and toggled available through the API, and both titles are stored.

## Follow-on — Book categories

Not part of the stage exit above. A book category is one flat label. One book has at most one category. The plan, rule, and cases are [api-book-categories.md](api-book-categories.md), [api-book-categories.mdc](../rules/api-book-categories.mdc), and [api-book-categories.md](../tests/api-book-categories.md).

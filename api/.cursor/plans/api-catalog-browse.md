# api-catalog-browse

Stage plan for student read-only catalog. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Sections 4.4 and 7.1, and Stage 3. This plan adds order only. It does not drop an exit check.

Required cases are in [api-catalog-browse.md](../tests/api-catalog-browse.md).

Start only after `api-library-catalog` exits. Do not start `api-purchase` until this stage exits.

Do not return video ids, notes, or material bytes. Do not create enrollments or orders.

Permission `CATALOG_BROWSE` is granted to `STUDENT` only. Admin catalog reads stay on `COURSE_READ` and `BOOK_READ`. Enforce the permission, not a raw role check copied through the service. The published-and-open filter is catalog business data, applied inside the catalog feature.

When the slice's checks finish, write `api/.cursor/progress/api-catalog-browse/slice-1-browse.md`.

## Slice 1 — Student browse and search

All paths under `/api/v1`, authenticated.

- `GET /catalog/courses?q=&page=&size=` and `GET /catalog/courses/{id}`.
- `GET /catalog/books?q=&page=&size=` and `GET /catalog/books/{id}`.
- `q` matches `title_en` or `title_bn`, case-insensitive. Other fields are not a second language and are not required to match.
- A course is included only when `status` is `PUBLISHED`, `closed` is false, and `deleted_at` is null. Draft, archived, and closed courses are absent from the list, the detail, and search.
- A book is included only when `available` is true and it is not soft-deleted.
- Course detail includes both titles, description, content language, price, delivery mode, access type, cover link, and the content tree as titles plus each lecture's `free` flag. It does not include `video_ref`, notes, or file bytes.
- Book detail includes both titles, author, description, price, and cover link.
- Cover download for a listed course or book is allowed to the browsing student through `GET /files/{id}`. Material files still return `403`, code `MATERIAL_FORBIDDEN`.
- An admin token without `CATALOG_BROWSE` does not use these routes. Admins keep using the admin reads from the earlier stages.

## Stage exit

A student can search and browse the catalog and see what is currently offered, cannot see a closed course, and cannot acquire or open lecture content.

## Follow-on — Student course read

Not part of the stage exit above. The plan and cases are [api-catalog-course-read.md](api-catalog-course-read.md) and [api-catalog-course-read.md](../tests/api-catalog-course-read.md). The rule is [api-catalog-course-read.mdc](../rules/api-catalog-course-read.mdc).

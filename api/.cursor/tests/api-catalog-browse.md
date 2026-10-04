# api-catalog-browse tests

Required cases for Phase 2 Stage 3. Written during implementation as `CatalogBrowseIT`. PostgreSQL only. No manual check.

Seed, as admins, one published open course, one closed course, one draft, one archived course, one available book, and one unavailable book. Each course and book has a distinct English title and Bangla title. Sign in as a student for the catalog calls.

## CatalogBrowseIT

- A student can list and open the published open course and the available book.
- `q` set to the English title and `q` set to the Bangla title both return that course. The same is true for the book.
- `q` does not need to match the description.
- The closed, draft, and archived courses are absent from the list, search, and detail (`404` on detail).
- The unavailable book is absent.
- Course detail includes the tree titles and each lecture's `free` flag, and omits `video_ref`, notes, and material bytes.
- The student can download that course's cover and the book's cover.
- The student cannot download a material file (`MATERIAL_FORBIDDEN`).
- A student token cannot call `POST /courses` (`403`).
- An unauthenticated catalog GET returns `401`.

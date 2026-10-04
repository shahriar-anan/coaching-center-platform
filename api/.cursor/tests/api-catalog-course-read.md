# api-catalog-course-read tests

Follow-on cases for the student course read. Written as `CatalogCourseReadIT`. PostgreSQL only. No manual check. Do not fold these into `CatalogBrowseIT` or `ContentAccessIT`.

Seed, as admins, one published open freemium course placed under Main Category → Sub Category. Give the course a cover, one course-level file that is not the cover, one free course-level file, one section, one lecture under that section, one lecture with no section, and one file on the section's lecture. Sign in as a student.

## CatalogCourseReadIT

- Course detail `categoryPath` is Main Category then Sub Category, titles only. The payload has no other course and no sibling category.
- A published open course with no category has an empty or absent `categoryPath`.
- `courseMaterials` lists the course-level files and omits the cover. Each item has `originalFileName`, `contentType`, `byteSize`, and `free`, and has no bytes.
- The content tree still has `parentId`. The lecture under the section points at that section. The lecture with no section has a null `parentId`.
- An allowed lecture returns `materials` with file name, content type, and size, and still returns `materialIds`.
- A paid lecture without an enrollment returns `403` `ENROLLMENT_REQUIRED` and does not include file names.
- Downloading a paid course-level file without an enrollment is still refused. Downloading a free course-level file, and the cover, still succeeds.
- `GET /course-categories` and `GET /book-categories` with the student token return `403`.
- Book detail has no `categoryId`.
- A closed course stays absent (`404` on detail).

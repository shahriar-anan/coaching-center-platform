# api-content-access — slice 1 (content gate)

## Done

- `CONTENT_READ` for students; lecture and course-material catalog routes; `GET /api/v1/me/courses`.
- `ContentAccessService` + `FileAccessService` integration for material bytes (`ENROLLMENT_REQUIRED` on routes, `MATERIAL_FORBIDDEN` on files).

## Tested

- `ContentAccessIT` — passed (local PostgreSQL via `TEST_DB_*`).
- `CatalogBrowseIT` updated for free-lecture file access — passed.

## Stage exit

Met.

## Next

- `mobile-catalog` slice 1 (home tabs).

# api-catalog-browse — slice 1 (student browse and search)

## Done

- `CATALOG_BROWSE` for students only; `GET /api/v1/catalog/courses` and `/books` with `q`, paging, and detail routes.
- Published open courses and available books only; course detail returns tree titles and lecture `free` flags (no video, notes, or materials).
- `FileAccessService`: students may download catalog covers; other materials return `MATERIAL_FORBIDDEN`.

## Tested

- `CatalogBrowseIT` — passed (local PostgreSQL via `TEST_DB_*`).

## Stage exit

Met.

## Next

- `api-purchase` slice 1 (`CoursePurchaseIT`).

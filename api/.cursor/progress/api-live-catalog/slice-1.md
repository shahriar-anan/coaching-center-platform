# Slice 1 — Categories and modules

Done. Flyway `V24__live_exam_catalog.sql` adds `live_exam_categories` and `live_exam_modules`. `exam_modules` and `exam_nodes` are unchanged.

A category tree stores Bangla and English titles, a parent, and a sibling position. A cycle is rejected. A module belongs to one category, uses access mode `MANUAL`, and is `DRAFT` until a separate final save and then publish. A later edit returns it to draft and requires final save again. Students read published modules at `GET /api/v1/live-exam-modules`. Staff read drafts there and on `GET /api/v1/live-exam-categories`. Delete and archive are Master Admin only. Delete sets `deleted_at` and later reads omit the row.

Roster filtering is not in this stage. Any student sees every published module.

`mvn test -Dtest=LiveCatalogIT` on PostgreSQL 17.9, database `coaching_center_test`. Flyway validated V1–V23 and applied V24. Tests run: 6. Failures: 0.

Next: `api-live-exam`.

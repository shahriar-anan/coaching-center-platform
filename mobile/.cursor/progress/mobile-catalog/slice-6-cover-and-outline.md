# Slice 6 — Cover and outline

## Done

- Course list and course detail load `coverFileId` through `GET /files/{id}` via `CatalogCover`.
- Content outline renders as nested section cards with sibling ordering by `position`.
- Course-level lectures (`parentId` null) appear under "Lectures on the course".

## Tested

- `flutter test` — cover/outline cases in `test/course_followon_test.dart` (pass).

## Next

- Slice 7 — category path chips on course detail.

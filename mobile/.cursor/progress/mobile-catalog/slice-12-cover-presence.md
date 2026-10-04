# Slice 12 — Cover presence, wait, and size

## Done

- `CatalogCover` only when `coverFileId` is set; list tiles omit leading when absent.
- Loading, failed, too-large, and cached display via `CoverByteCache` (2 MB decode cap).

## Tested

- `flutter test` — cover reuse and UI cases in `test/course_followon_test.dart` and `test/course_ui_test.dart` (pass).

## Next

- Slice 13 — placement path.

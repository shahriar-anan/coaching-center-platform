# Slice 11 — PDF handoff and file names

## Done

- PDFs hand off via `open_filex` after writing under a per-file-id temp folder; no in-app PDF page.
- `sanitizeDownloadLeafName` keeps Bangla (and other Unicode) in the leaf name.
- Android manifest queries `ACTION_VIEW` for `application/pdf`.
- `catalog.pdfOpenFailed` when no viewer is available.

## Tested

- `flutter test` — PDF handoff and file cases in `test/course_followon_test.dart`, `test/safe_file_name_test.dart` (pass).

## Next

- Shahriar: phone exit (`mobile/.cursor/progress/mobile-catalog/phone-exit.md`).

# Slice 8 — Files

## Done

- Course detail lists `courseMaterials` by `originalFileName`; cover id is excluded from that list.
- Allowed images open in-app; PDFs go through `FileOpener` (platform viewer).
- Lecture screen lists `materials` with file names when the lecture is allowed; locked lectures show no names.
- `ENROLLMENT_REQUIRED` / `MATERIAL_FORBIDDEN` map to locked messaging without showing bytes.

## Tested

- `flutter test` — file cases in `test/course_followon_test.dart` and updated `test/course_detail_test.dart` (pass).
- `dart analyze` on `mobile/` (pass; two info lints in test fixtures only).

## Next

- Slices 9–11 correct entitlement, sibling order, and PDF handoff.

# Slice 9 — Freemium entitlement

## Done

- `GET /me/courses` on each course detail load; enrollment match drives outline and course-file lock hints.
- `CatalogAccess` centralizes full-free, free flag, and enrolled rules. Failed `GET /me/courses` leaves enrollment unknown (no false enrolled).
- Paid rows still open the lecture screen; file bytes still gated by `GET /files/{id}`.

## Tested

- `flutter test` — entitlement cases in `test/course_followon_test.dart` (pass).

## Next

- Slice 10 — sibling order (implemented in same pass as slice 9 follow-up).

# mobile-catalog — slice 3 (course detail and content)

## Done

- Catalog API: `getCourse`, `getLecture`, `downloadFile` (authorized; no public file URLs).
- `CourseDetailScreen` (tree from catalog payload), `LectureScreen` (player, notes, materials), `LecturePlayer` widget.
- Course list navigates to detail; free / full-free / paid lecture access behavior per API codes.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (45 tests, including `course_detail_test.dart`) |
| `dart analyze` | No issues |

## Manual QA

None for this slice.

## Next

- Slice 4: purchase and history.

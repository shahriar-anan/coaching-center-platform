# mobile-catalog — slice 1 (home tabs)

## Done

- `HomeScreen`: student name from `GET /me` in the app bar; Courses and Library tabs with placeholder panes; logout unchanged.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (34 tests, including `home_screen_test.dart`) |
| `dart analyze` | No issues |

## Manual QA

None for this slice (phone exit is after slice 5).

## Next

- Slice 2: browse and search (`flutter test` for catalog lists).

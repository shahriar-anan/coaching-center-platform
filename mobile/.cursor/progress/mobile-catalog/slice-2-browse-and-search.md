# mobile-catalog — slice 2 (browse and search)

## Done

- `AuthApi.listCourses` / `listBooks` with `q` query param and bearer auth.
- `CatalogBrowseTab` on each home tab: search field, list, empty and load-failure strings.
- Locale-aware primary title (EN/BN) on list rows.

## Tested

| Check | Result |
|---|---|
| `flutter test` | Passed (40 tests, including `catalog_browse_test.dart`) |
| `dart analyze` | No issues |

## Manual QA

None for this slice.

## Next

- Slice 3: course detail and content.

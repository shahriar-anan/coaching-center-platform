# NR-01 final check, W3, case 15 (W3 part): audit rows for the catalog actions

**Outcome:** new test, green. No production change needed.

**Test:** `api/src/test/java/com/coachingcenter/api/exams/CatalogAuditIT.java`, 1 test:
`eachCatalogActionWritesOneAuditRowWithItsActorAndEntity`.

Each action is run once over HTTP, and an `audit_log` row is counted for the action, entity and actor:
- `LIVE_EXAM_PACKAGE_EXAM_ADD` (system admin, package): exam added to a published package (staged).
- `LIVE_EXAM_PACKAGE_EXAM_REMOVE` (system admin, package): exam removed (staged).
- `LIVE_EXAM_MODULE_REVISION_PUBLISH` (master admin, package): staged change published.
- `LIVE_EXAM_MODULE_REVISION_DISCARD` (system admin, package): staged title edit discarded.
- `LIVE_EXAM_POPULAR_SET` (master admin): on the package (entity `LIVE_EXAM_MODULE`) and on an exam (entity `LIVE_EXAM`).

Each count is exactly 1.

**Already covered:** `PackageRevisionIT` covers the staged edit and discard behaviour but not the audit rows. `PopularIT` covers the popular flag, not the audit row. No test asserted these audit rows before.

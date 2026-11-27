# Progress L-fix: the offline tests that used the removed instructor routes

Fixes the 12 failures (405 on `POST /api/v1/offline-instructors`) that L2 left in two test classes that L does not own. Assertions are
unchanged; only the setup changed.

Changed:
- `OfflineClassIT`: the `instructor(...)` helper, the `ensureKnown` helper and the rename in `makeUnknown` no longer call the removed
  routes. They use two new helpers: `newInstructor(name)` makes a payee of the Offline instructors group with `AccTestData.payee`
  (which also writes the `offline_instructors` row under the same id), then sets the name on both rows; `renameInstructor(id, name)`
  sets the name and clears the aliases on the instructor row and its payee, as the ledger's edit does.
- `OfflineRoutineIT`: the same helpers; the milon instructor is made with `newInstructor`, and its id is read as a UUID (the three
  `milon.get("id").asText()` reads became `milon.toString()`, the same text).
- Both files import `AccIds` and `AccTestData` from the accounting test package.

Grep of `api/src/test` for the removed routes (after the fix):
- `offline-instructor-payments`: no match.
- `pay-summary`: no match.
- `POST`/`PATCH` `/api/v1/offline-instructors` (create, edit, archive, payments): no match. Built sub-paths (`offline-instructors/`): no match.
- Remaining references are the kept `GET /api/v1/offline-instructors` (picker and staff search, in OfflineClassIT, OfflineRoutineIT,
  OfflineInstructorDataIT, AccPayeeIT), the `AuditLogIT` route entry for it, and one comment in AccPayeeIT.
- The web tests under `web/tests/batch-pay/` mention the path. Those are W's files and were not in this check.

Tests (`acc-mvn.ps1 -Agent L -Tests "OfflineClassIT,OfflineRoutineIT,OfflineInstructorDataIT,AuditLogIT"`): all green.
- OfflineClassIT 6 run, 0 failures (was 5 failing)
- OfflineRoutineIT 11 run, 0 failures (was 7 failing)
- OfflineInstructorDataIT 4 run, 0 failures
- AuditLogIT 3 run, 0 failures

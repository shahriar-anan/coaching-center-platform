# B4: package fields and staged package edits (agent B)

Committed together with B1 to B3 and B5 to B6 (shared files).

## Changed
- `PackageRevisionService` (staged draft in `live_exam_module_revisions.payload`; change list; discard; publish with `revisionId`, stale refused; change log; audit), `PackageRevisionController` (`pending-changes` GET and DELETE).
- `LiveExamModuleService`: `update` stages on a published package (status stays PUBLISHED); `publish(id)` overload kept for compatibility, and `publish(id, revisionId)`; `discountPercent` and `discountEndsAt` validated by `ExamPricing`.
- `LiveExamModuleController.publish`: Master Admin for a revision; missing `revisionId` accepted from the Master Admin only (contracts §7 item 4), logged at warn.
- Price changes still need `EXAM_PRICE` (Master Admin only). The discount fields are not behind that check; see the question in the report.

## Tests: `PackageRevisionIT` 6/6 (green)
- Test fixes: the price is changed by the Master Admin (only it holds `EXAM_PRICE`); the description expectation matches the fixture; the start time moved to 10:10 (content lock).
- Decided behaviour change: a published package edit is staged, not reset to DRAFT (`LiveCatalogIT` updated, E34). Stated in the commit.

## Discount permission (orchestrator answer)
- `discountPercent` and `discountEndsAt` need `EXAM_PRICE`, the Master Admin's permission, as the price does. A System Admin who sends them gets 403 `ACCESS_DENIED`, on a draft and on a staged revision. Test: `PackageRevisionIT.aSystemAdminCannotStageADiscountButTheMasterAdminCan`.

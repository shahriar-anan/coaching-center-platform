# B1: package membership (agent B)

Committed together with B2 to B6 (the code is shared; see b-b2.md).

## Changed
- `PackageMembershipService` and `PackageMembershipController`: add (`POST …/exams/{examId}`), remove (`DELETE`), reorder (`PATCH …/exams/order`). Published packages stage the change (B4); drafts apply at once.
- `LiveExamPackageExamRepository`: active-by-exam-id query, all-active query, `findActiveByModuleId` used by delete.
- `LiveExamModuleService.delete` ends the package's memberships (`removed_at`).

## Tests: `PackageMembershipIT`, last run: 3 of 4 pass
- Passing: same exam in two packages; duplicate add rejected; reorder and remove, then add back; (the reorder test is corrected, see below).
- FAILING: `aStudentWhoHeldThePackageBeforeARemovalKeepsTheExamAndALaterHolderDoesNot`, line 188. The early holder's `take` returns 404, while `canSit` is true. The 404 comes from `LiveExamService.get` → `visibleModule` (line ~733), which requires a primary membership. After removal the exam has none, so the visibility check fails. That file is A's. The rule needed is the one contracts §8 gives for public and student visibility: home category branch. **Needs the orchestrator or A to change `LiveExamService.visibleModule` and `primaryModuleId` in `get`/`publish` (publish of a standalone exam also returns 404 today).**

## Test changes (not weakened)
- `reorderAndRemove` test: the package is published, so the order, removal and re-add are staged. The test now has the Master Admin publish after each change (decided behaviour, E34 and B4).
- Access test: exam start moved from 10:00 to 10:10 (the content lock, which A is removing, rejected question adds at the start time).

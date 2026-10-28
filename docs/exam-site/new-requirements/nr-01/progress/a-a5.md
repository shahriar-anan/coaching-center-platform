# A5: changes-waiting badge (agent A)

## What changed

- `GET /api/v1/live-exam-pending-changes` (`PendingChangesController`): `{exams, packages, items}`, newest first. Each item is `{kind: EXAM | PACKAGE, id, titleEn, titleBn, updatedAt, updatedBy}`. Master Admin and System Admin (`EXAM_MANAGE`); students get `403`.
- Reads the exam revisions and the package revisions (`live_exam_module_revisions`), so a package that B stages shows up here without a change in A's code.

## Tests

One invocation, exit 0 (`ExamRevisionIT,AuditLogIT`):

| Class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `ExamRevisionIT` | 12 | 0 | 0 | 1 (`aStagedPackageAppearsOnTheChangesWaitingBadge`, `@Disabled("needs B")`) |
| `AuditLogIT` | 3 | 0 | 0 | 0 |

- `theChangesWaitingBadgeListsStagedExamsNewestFirstAndOnlyForStaff`: two published exams, staged in order with the clock advanced between them; the later one is listed first, and a student gets `403`.
- `aStagedPackageAppearsOnTheChangesWaitingBadge`: `@Disabled("needs B")`. It needs B's package staging on `nr01/integration`. Orchestrator to re-enable it once B's package revision is merged.

## API changes

`AuditLogIT`'s A block lists `GET /api/v1/live-exam-pending-changes`.

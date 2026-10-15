# api-exam-module

Stage plan for Phase 3 Stage 1. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Sections 2, 3, 4.1, and 4.2, and Stage 1. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-module.md](../tests/api-exam-module.md). The stage rule is [api-exam-module.mdc](../rules/api-exam-module.mdc). The glanceable list is [phase-3-status.md](../../../docs/phases/phase-3/phase-3-status.md).

Start once Phase 2's API stages have exited. Phase 2's phone exit and browser exit do not block this stage. Do not start `api-exam-questions` until this stage's exit passes.

Do not build question records, components, access modes, course links, purchases, attempts, written uploads, results, the question bank, student catalog routes, mobile screens, or web screens. Do not add a node to a course's content tree.

Package: `com.coachingcenter.api.exams`. Register permissions from `ExamPermissionContributor` in that package. Do not edit the permission checker. Only `MASTER_ADMIN` receives `EXAM_DELETE`.

When a slice's checks finish, write `api/.cursor/progress/api-exam-module/slice-<number>-<name>.md` before the next slice.

## Permissions

| Permission | Master Admin | System Admin | Student |
|---|---|---|---|
| `EXAM_READ` | yes | yes | no |
| `EXAM_MANAGE` | yes | yes | no |
| `EXAM_PRICE` | yes | no | no |
| `EXAM_DELETE` | yes | no | no |

`EXAM_MANAGE` creates and edits both titles, both descriptions, the tree, and moves a module between draft and published. It does not set the price, archive, or delete.

A System Admin request that sets or changes `price` returns `403`. Price may be null in this stage. Publishing does not require a price. Access mode does not exist yet.

## Slice 1 — Module record

Flyway `V14__exam_modules.sql`. One `exam_modules` table extending the base entity (`id`, `created_at`, `updated_at`, `deleted_at`).

Columns: `title_en`, `title_bn`, `description_en`, `description_bn`, `price` numeric nullable, `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`).

- `POST /api/v1/exam-modules` and `PATCH /api/v1/exam-modules/{id}` under `EXAM_MANAGE`, with the price rule above.
- Both titles and both descriptions are required on create. A blank value is `400`, code `VALIDATION_FAILED`. Description is the module overview, not a paper's rules.
- `GET /api/v1/exam-modules` and `GET /api/v1/exam-modules/{id}` under `EXAM_READ`. The list uses `page` and `size` and the shared page envelope. Soft-deleted rows are absent. Detail returns all four bilingual fields.
- `DELETE /api/v1/exam-modules/{id}` is a soft delete under `EXAM_DELETE`. Write `audit_log` with `actor_user_id`, `action` `EXAM_MODULE_DELETE`, `entity_type` `EXAM_MODULE`, and `entity_id`.
- Archiving is `POST /api/v1/exam-modules/{id}/archive` under `EXAM_DELETE`, audited as `EXAM_MODULE_ARCHIVE`.
- There is no cover, no access-duration column, and no expiry column.

## Slice 2 — Content tree

Flyway `V15__exam_nodes.sql`. Table `exam_nodes`: module id, nullable parent id, `node_type` (`SECTION` or `PAPER` only), `title_en`, `title_bn`, `position`.

- A section may contain sections or papers. A paper is a leaf.
- Papers may sit directly under the module. The root parent is null.
- Both titles are required. Nodes have no description columns.
- `POST /api/v1/exam-modules/{id}/nodes`, `PATCH /api/v1/exam-modules/{id}/nodes/{nodeId}`, and `POST /api/v1/exam-modules/{id}/nodes/reorder` under `EXAM_MANAGE`.
- Reorder and move keep positions explicit among siblings. Moving a node sets its position to the end of the new parent. Reject a cycle, a paper used as a parent, and any type other than section or paper (`400`, code `NODE_TYPE_REJECTED`).
- `DELETE` of a node is `EXAM_DELETE` and soft-deletes that node. A System Admin delete stays `403`.
- A section delete returns `409`, code `SECTION_NOT_EMPTY`, when that section or any subsection under it still has a paper. Nothing is deleted in that case. A section whose subsections contain no paper can be deleted, and those empty subsections are soft-deleted with it.

## Stage exit

A module with a multi-level tree of sections and papers can be created, edited, and reordered through the API. Every module title, module description, section name, and paper name is entered and retrieved in both Bangla and English.

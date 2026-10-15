# api-exam-module tests

Required cases for Phase 3 Stage 1. Java `*IT` classes are written during implementation, not as part of planning.

Run every class against PostgreSQL. Use Testcontainers in CI. Locally, use `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` when set. Do not use H2.

Shared setup: a Master Admin and a System Admin from the existing test bootstrap.

No manual check. The classes below are the slice checks. Write the progress note after the class passes.

## ExamModuleIT

Slice 1.

- Create stores `title_en`, `title_bn`, `description_en`, and `description_bn`. A missing or blank title or description is rejected.
- The JSON uses those four fields. Detail returns the same values that were stored.
- The `exam_modules` table has no access-duration or expiry column.
- A System Admin can create and edit titles and descriptions and cannot set `price` (`403`).
- A Master Admin can set `price`. Publishing with a null price succeeds in this stage.
- List and detail use the page envelope and hide soft-deleted modules.
- A System Admin `DELETE` returns `403`. A Master Admin soft delete hides the module and writes `audit_log` `EXAM_MODULE_DELETE` with actor, entity type `EXAM_MODULE`, and entity id.
- A System Admin archive returns `403`. A Master Admin archive writes `EXAM_MODULE_ARCHIVE`.
- An unauthenticated `GET` returns `401`.

## ExamTreeIT

Slice 2.

- A module can contain a section, a nested section, and a paper under that nested section, plus a paper directly under the module.
- Each node stores and returns `title_en` and `title_bn`. A node response has no description field.
- Reorder changes sibling positions. A move onto a paper, a cycle, and a node type other than `SECTION` or `PAPER` return `NODE_TYPE_REJECTED`.
- Deleting a node requires `EXAM_DELETE`. A System Admin delete returns `403`.
- Deleting a section that has a paper, or a subsection that has a paper, returns `409`, code `SECTION_NOT_EMPTY`, and leaves that section and its paper in the list.
- Deleting a section whose only descendant is an empty subsection removes both.
- Moving a paper to another section, or to the module root, updates `parentId` and places it at the end of the new siblings.

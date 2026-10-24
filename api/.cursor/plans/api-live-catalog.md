# api-live-catalog

**Outdated (2026-10-23):** access mode for this delivery is `MANUAL` only. Public catalog reads and purchase are later work. The rest of this stage record stands, including bilingual titles, draft then publish, and students not seeing drafts.

Stage 1 of the live exam plan. Spec: `docs/phases/phase-3/phase-3-scope.md` Sections 2 and 3. Tests: `api/.cursor/tests/api-live-catalog.md`. Rule: `api/.cursor/rules/api-live-catalog.mdc`.

Do not build exams, the roster, sittings, or evaluation in this stage. Do not add rows to the previous `exam_modules` or `exam_nodes` tables for this model.

Flyway starts at `V24`.

## Slice 1 — Categories and modules

- A category has `title_en`, `title_bn`, a nullable parent, and a position among its siblings. Both titles are required. A cycle is rejected.
- A module has both titles, both descriptions, one category id, and status `DRAFT` or `PUBLISHED`. Access mode for this delivery is `MANUAL`.
- `POST` and `PATCH` under the staff manage permission store a draft. A separate final-save action is required before publish. Publish of a module that has not been finally saved is rejected.
- `GET` for a student returns published modules only. Drafts are on the staff read.
- Delete and archive of a category or module are Master Admin only. A System Admin receives `403`. Delete of a category that still has a subcategory or a module is rejected. Archive of that category succeeds and hides the whole branch from students. A new child, a new module, or a move onto an archived category is rejected. Staff still read the archived branch.
- Two modules may share one category. A module may sit directly under a top-level category or deeper.

## Exit

A category tree of more than two levels can be created. Two modules can sit in the same category. A student does not see a module until it is published, and publish requires a final save.

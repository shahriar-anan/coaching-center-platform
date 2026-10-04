# api-course-categories

Follow-on to [api-course-catalog.md](api-course-catalog.md). Not part of that stage's exit. A category groups whole courses. A section is a folder inside one course. Do not store a category as a `content_nodes` row.

Required cases are in [api-course-categories.md](../tests/api-course-categories.md). The rule is [api-course-categories.mdc](../rules/api-course-categories.mdc).

Packages stay under `com.coachingcenter.api.courses`. Reuse `COURSE_READ`, `COURSE_MANAGE`, and `COURSE_DELETE`. Do not add a permission. Only `MASTER_ADMIN` can delete.

When `CourseCategoryIT` passes, write `api/.cursor/progress/api-course-catalog/slice-5-course-categories.md`.

## Storage

Flyway `V7__course_categories.sql`.

`course_categories`: nullable `parent_id` referencing `course_categories`, `title_en`, `title_bn`, `position`, plus the base entity columns. A null parent is a main category.

`courses` gains nullable `category_id` referencing `course_categories` and nullable `category_position`. Both are null, or both are set.

Under one category, child categories and courses share one position sequence. A course has at most one category. A course is not a category and cannot be a parent.

## API

- `GET /api/v1/course-categories` under `COURSE_READ` returns the tree. Each node has `kind` `CATEGORY` or `COURSE`. A category node has `children`. A course node does not. Children are ordered by the shared position. Soft-deleted rows are absent.
- `POST /api/v1/course-categories` and `PATCH /api/v1/course-categories/{id}` under `COURSE_MANAGE`. Both titles are required on create. `parentId` null means a main category. An omitted `position` appends after the current siblings. A position already used by a sibling category or course returns `409`, code `CATEGORY_POSITION_TAKEN`. A parent that is the category itself or its descendant returns `400`, code `CATEGORY_CYCLE`.
- `PATCH /api/v1/courses/{id}` may set `categoryId` and `categoryPosition` under `COURSE_MANAGE`. `categoryId` null clears the placement. The course read model includes `categoryId` and `categoryPosition` when set.
- `DELETE /api/v1/course-categories/{id}` is a soft delete under `COURSE_DELETE`, audited as `COURSE_CATEGORY_DELETE` with entity type `COURSE_CATEGORY`. A category that still has a subcategory or a course returns `409`, code `CATEGORY_NOT_EMPTY`.

A missing category or course returns `404`, code `RESOURCE_NOT_FOUND`. A student or a missing token cannot read the tree (`403` or `401`).

The student app does not call this route. A placed course exposes only its own category titles on the catalog course detail, in [api-catalog-course-read.md](api-catalog-course-read.md). That path is not this tree.

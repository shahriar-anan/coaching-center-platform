# api-course-categories tests

Follow-on cases. Not part of the `api-course-catalog` stage exit. The class is `CourseCategoryIT` against PostgreSQL. Do not use H2. Do not add these assertions to `CourseTreeIT`.

The Master Admin creates every course in this class. A System Admin is used only where the case says so.

## CourseCategoryIT

- A Master Admin builds one list under a main category, in order: Sub Category-1, Course-1, Sub Category-2, Course-2. `GET /course-categories` returns that order, with `kind` `CATEGORY` or `COURSE` and the shared positions `0` through `3`. Course-1 and Course-2 read back with that main category's id. The subcategory is not a root.
- Creating those categories inserts no `content_nodes` row. On Course-1, a lecture may omit `parentId`, and another lecture may sit under a section.
- A course moved from the main category to a subcategory remains in the tree once, under the subcategory. A second course placed on a different main category stays on that category.
- `categoryId` null clears placement. A soft-deleted course disappears from the tree. Its category can then be deleted.
- A System Admin can create a category and cannot delete one (`403`). A Master Admin delete of a category that still has a subcategory or a course returns `409`, code `CATEGORY_NOT_EMPTY`. A Master Admin delete of an empty category is a soft delete, audits `COURSE_CATEGORY_DELETE` with actor and entity id, and the category is absent from the tree.
- A student `GET` returns `403`. A missing token returns `401`. Missing titles return `400`, code `VALIDATION_FAILED`. A parent cycle returns `400`, code `CATEGORY_CYCLE`. A sibling position already used returns `409`, code `CATEGORY_POSITION_TAKEN`. An unknown parent returns `404`.

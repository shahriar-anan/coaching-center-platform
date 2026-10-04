# api-catalog-course-read

Follow-on to [api-catalog-browse.md](api-catalog-browse.md) and [api-content-access.md](api-content-access.md). Not part of either stage exit. The student course screen needs the cover it already receives, the category this course sits in, and the files attached to the course. Search, purchase, and the content gate stay as they are.

Required cases are in [api-catalog-course-read.md](../tests/api-catalog-course-read.md). The rule is [api-catalog-course-read.mdc](../rules/api-catalog-course-read.mdc).

Start only after `api-content-access` has exited. Implement this before mobile slices 7 and 8 in [mobile-catalog.md](../../../mobile/.cursor/plans/mobile-catalog.md). Mobile slice 6 uses fields the catalog already returns.

When `CatalogCourseReadIT` passes, write `api/.cursor/progress/api-catalog-browse/slice-2-course-read.md`.

## What this does not change

- `GET /api/v1/catalog/courses` stays a title search and may take optional `categoryId` (includes descendant categories). Student browse uses `GET /api/v1/catalog/course-categories` (categories only).
- `GET /api/v1/course-categories` and `GET /api/v1/book-categories` stay admin routes. A student still receives `403` on those admin URLs.
- Student library browse may filter with `categoryId` and list categories via `GET /api/v1/catalog/book-categories`.
- Book list and book detail gain no `categoryId` and no category title.
- Who may open a lecture, a course-level material, or `GET /files/{id}` stays the content-access rule. This follow-on adds names, not a new grant.
- Course detail still omits `video_ref`, notes, and file bytes. The cover stays a file id, not bytes.

## Course detail

`GET /api/v1/catalog/courses/{id}` keeps its current fields, including `coverFileId` and `contentTree` (`id`, `parentId`, `nodeType`, `title`, `position`, `free`).

It also returns:

- `categoryPath`: the categories from the main category down to the category this course is placed on. Each item is `titleEn` and `titleBn` only. No sibling category, no other course, no `kind`. An unplaced course returns an empty list. Jackson may omit the empty list; the client treats absence as unplaced.
- `courseMaterials`: materials whose `lecture_node_id` is null and whose id is not the course `coverFileId`. Each item is `id`, `originalFileName`, `contentType`, `byteSize`, and `free`. No stored file name and no bytes. Order is stable. A paid file is listed the same way a paid lecture title is listed. Opening it still follows the existing file gate.

## Lecture files

`GET /api/v1/catalog/courses/{id}/lectures/{lectureId}` keeps `materialIds` when the lecture is allowed. It also returns `materials`: the same ids, each with `originalFileName`, `contentType`, and `byteSize`. A refusal is unchanged (`403` `ENROLLMENT_REQUIRED`, or `404` when the course is closed). A refusal does not include those file names.

## Stage note

`CatalogBrowseIT` and `ContentAccessIT` stay the stage exits. `CatalogCourseReadIT` is the follow-on check only.

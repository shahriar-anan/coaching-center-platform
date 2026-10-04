# api-course-catalog tests

Required cases for Phase 2 Stage 1. Java `*IT` classes are written during implementation, not as part of planning.

Run every class against PostgreSQL. Use Testcontainers in CI. Locally, use `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` when set. Do not use H2.

Shared setup: a Master Admin and a System Admin from the existing test bootstrap, `FILE_STORAGE_DIR` pointed at a temp directory, and `MATERIAL_MAX_BYTES` set below 100 MB so the rejection case stays small. One test reads the default property and asserts it is 104857600.

No manual check. The classes below are the slice checks. Write the progress note after the class passes.

## CourseCatalogIT

Slice 1.

- Create stores `title_en`, `title_bn`, and one of `BN` or `EN`. A missing title is rejected.
- Description is a single field. The JSON has no second description.
- The courses table has no access-duration or expiry column.
- A System Admin can create and edit a course and cannot set `price` (`403`).
- A Master Admin can set `price`. Publishing a freemium course with no price returns `PRICE_REQUIRED`. A full-free course publishes with no price.
- Changing `access_type` after create returns `ACCESS_TYPE_FIXED`.
- List and detail use the page envelope and hide soft-deleted courses.
- A System Admin `DELETE` returns `403`. A Master Admin soft delete hides the course and writes `audit_log` `COURSE_DELETE` with actor and entity id.
- A System Admin archive returns `403`. A Master Admin archive writes `COURSE_ARCHIVE`.

## CourseTreeIT

Slice 2.

- A course can contain a section, a nested section, and a lecture under that nested section, plus a lecture directly under the course.
- Reorder changes sibling positions. A move onto a lecture, a cycle, and a node type other than `SECTION` or `LECTURE` return `NODE_TYPE_REJECTED`.
- A freemium course accepts a lecture with `free` false, and a course whose every lecture is paid.
- Setting a free lecture back to paid returns `FREE_CONTENT_FIXED`. Marking an additional lecture free succeeds.
- On a full-free course the lecture `free` flag does not create a paid lecture.
- Deleting a node requires `COURSE_DELETE`. A System Admin delete returns `403`.
- Deleting a section that has a lecture, or a subsection that has a lecture, returns `409`, code `SECTION_NOT_EMPTY`, and leaves that section and its lecture in the list.
- Deleting a section whose only descendant is an empty subsection removes both.
- Deleting a lecture removes that lecture and the files attached to it. A course-level file remains.
- Moving a lecture to another section, or to the course root, updates `parentId` and places it at the end of the new siblings.

## CourseContentIT

Slice 3.

- A YouTube watch URL, a `youtu.be` URL, and an embed URL store `YOUTUBE` and the video id. A non-YouTube URL returns `INVALID_YOUTUBE_URL`.
- Notes are stored as one text field.
- A lecture material and a course-level material upload into `FILE_STORAGE_DIR` under an unguessable name. The directory is not a public static path.
- A course-level material can be marked free. Clearing that flag returns `FREE_CONTENT_FIXED`.
- A material upload whose `lectureNodeId` is a section returns `400`, code `VALIDATION_FAILED`.
- `PUT` replaces a lecture file and a course-level file, keeps the same id and lecture link, and keeps a free flag. An over-limit replacement returns `413` and the previous bytes remain.
- `DELETE` of a lecture file and of a course-level file is `COURSE_DELETE`, hides the row, and removes the stored bytes. A System Admin delete returns `403`. The cover is not deleted by this route.
- A null `videoUrl` clears `video_provider` and `video_ref`.
- An upload over `MATERIAL_MAX_BYTES` returns `FILE_TOO_LARGE` and stores nothing.
- The default max is 100 MB when the test property is unset.
- An admin with `COURSE_READ` can download the file. An unauthenticated GET returns `401`.

## CourseCloseIT

Slice 4.

- A System Admin close or reopen returns `403`.
- The first publish sets `opening_number` to 1 and `closed` false.
- Close on a published course sets `closed` true, leaves `opening_number` unchanged, and audits `COURSE_CLOSE`.
- Reopen increments `opening_number`, clears `closed`, and audits `COURSE_REOPEN`.
- Close on a draft or archived course is rejected.
- Admin detail still returns a closed course. The stored flags a later student query must use are `status`, `closed`, and `opening_number`.

## CourseCategoryIT

Follow-on. Cases are in [api-course-categories.md](api-course-categories.md). Do not add them to `CourseTreeIT`.

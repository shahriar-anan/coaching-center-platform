# api-course-depth

Follow-on to [api-course-catalog.md](api-course-catalog.md), [api-content-access.md](api-content-access.md), and [api-catalog-course-read.md](api-catalog-course-read.md). Not part of those stage exits. The course record, tree, purchase, and content gate stay. This plan adds the course fields and reads those exits do not have.

Required cases are in [api-course-depth.md](../tests/api-course-depth.md). The rule is [api-course-depth.mdc](../rules/api-course-depth.mdc).

Packages stay `courses`, `catalog`, `purchases`, and `files`. Reuse `COURSE_READ`, `COURSE_MANAGE`, `COURSE_DELETE`, and `CONTENT_READ`. Do not add a permission. Only `MASTER_ADMIN` can delete.

Web screens and the mobile app are out of scope. New JSON fields are additive. Existing clients keep working if they ignore them.

Do not add batches, exams, attendance, notices, a Teacher login role, a second video host, rich-text notes, or a progress report for admins. Do not change close, reopen, access type, or the rule that a free lecture or free course-level material cannot become paid. Do not move a file between the course, a section, and a lecture after upload.

Start at slice 6 only after `api-course-catalog` has exited. Do not start a later slice until that slice's progress note exists. Write the note under `api/.cursor/progress/api-course-catalog/`.

`CourseContentIT`, `CourseTreeIT`, `ContentAccessIT`, and `CatalogCourseReadIT` stay as they are. A section id in `lectureNodeId` is still `400` `VALIDATION_FAILED`. `courseMaterials` stays files with no lecture and no section, excluding the cover.

## Slice 6 — Material record

Flyway `V9__material_record.sql`.

`materials` gains:

- `title` `varchar(500)`. Backfill course and book rows from `original_file_name`. Then not null.
- `description` text, nullable.
- `kind` `varchar(16)` not null. One of `HANDOUT`, `EXERCISE`, `SLIDE`, `OTHER`. Backfill `OTHER`.
- `position` integer not null. Within each attachment group, backfill from `created_at` ascending, starting at 0.
- `section_node_id` uuid, nullable, referencing `content_nodes`. Null on every existing row.

An attachment group is one of: course-level (both node ids null), one lecture (`lecture_node_id` set), or one section (`section_node_id` set). A row has at most one of those node ids. Book rows keep both null.

List order for a group is `position` ascending. The backfill keeps today's `created_at` order.

- `POST /api/v1/courses/{id}/materials` stays one file under `COURSE_MANAGE`. Optional form fields `title`, `description`, and `kind`. A missing title stores `original_file_name`. A missing kind stores `OTHER`. Position appends in that group. `lectureNodeId` still rejects a section. New optional `sectionNodeId` attaches the file to a section in this course. Sending both node ids, a lecture in `sectionNodeId`, a section in `lectureNodeId`, or a node from another course returns `400` `VALIDATION_FAILED`.
- `POST /api/v1/courses/{id}/materials/batch` accepts repeated file parts named `files`, under `COURSE_MANAGE`. Optional repeated `titles`, `descriptions`, and `kinds` align by index. The same node-id rules apply to the whole batch. The response is the created views in position order, status `201`. One file over `MATERIAL_MAX_BYTES` returns `413` `FILE_TOO_LARGE` and stores none of the batch.
- `PATCH /api/v1/courses/{id}/materials/{materialId}` still sets `free` for a course-level or section file on a freemium course. Once true, it cannot be cleared (`409` `FREE_CONTENT_FIXED`). A lecture file still cannot take its own free flag (`400` `VALIDATION_FAILED`). The same PATCH may set `title`, `description`, `kind`, and `position`. `description` null clears it. `position` renumbers that group. Kind outside the four values is `400` `VALIDATION_FAILED`. The PATCH does not change either node id.
- `PUT` replace and `DELETE` are unchanged. Replace keeps title, description, kind, position, node link, and free flag, and still updates the stored file. Download `Content-Disposition` still uses `original_file_name`.
- Deleting a lecture still removes its files. Deleting a section that has no lecture still follows `SECTION_NOT_EMPTY`. When that delete succeeds, files on that section are soft-deleted and their bytes are removed.
- Admin `GET` material views add `title`, `description`, `kind`, `position`, and `sectionNodeId` when set. Omit a null `sectionNodeId`, as a null `lectureNodeId` is omitted today.

Student reads, same gate as today:

- `courseMaterials` items add `title`, `description`, `kind`, and `position`.
- Lecture `materials` add the same four fields. `materialIds` stays.
- Course detail gains `sectionMaterials`: files with a `sectionNodeId`. Each item has `id`, `sectionNodeId`, `title`, `description`, `kind`, `position`, `originalFileName`, `contentType`, `byteSize`, and `free`. No stored name and no bytes. A paid section file is listed the way a paid lecture title is listed.
- `GET /api/v1/catalog/courses/{id}/materials/{materialId}` also describes a section file the student may open. A section file opens when the course is open and one of these holds: the course is `FULL_FREE`, the file is free, or the student has an active enrollment for the current opening. A lecture file on this route is still `404`. Refusal for a closed course stays `404`. Paid content without a current enrollment stays `403` `ENROLLMENT_REQUIRED`.
- `GET /api/v1/files/{id}` uses that same rule for a section file.

## Slice 7 — Lecture summary

Flyway `V10__lecture_summary.sql`. `content_nodes` gains nullable `description` text and nullable `duration_minutes` integer.

`PATCH` on a lecture accepts `description` and `durationMinutes`. Null clears either one. `durationMinutes` must be at least 1 when set; `0` or a negative value returns `400` `VALIDATION_FAILED`. Sending either field on a section returns `400` `VALIDATION_FAILED`.

Admin node views include both. Student course detail `contentTree` includes `description` and `durationMinutes` on lectures. They are outline text, like the title. Notes and `video_ref` stay on the lecture route and stay gated. An allowed lecture payload includes `description` and `durationMinutes`. A refusal does not.

## Slice 8 — Progress

Flyway `V11__lecture_progress.sql`. Table `lecture_progress`: student id, course id, lecture node id, `watched` boolean not null default false, `last_opened_at` timestamptz not null, plus the base entity columns. One live row per student and lecture.

`POST /api/v1/me/courses/{courseId}/progress` with `lectureId` and optional `watched`. The caller must be a student who may open that lecture under the content gate, including a full-free course with no enrollment. Otherwise the refusal matches the lecture route (`404` when the course is closed or the node is not a lecture, `403` `ENROLLMENT_REQUIRED` when it is paid). Success sets `last_opened_at` to now. On create, omitted `watched` stores false. On update, omitted `watched` leaves the flag. The response is `lectureId`, `watched`, and `lastOpenedAt`.

`GET /api/v1/me/courses` adds `lastLectureId` on each course: the lecture with the latest `last_opened_at` for that student and course, or null when none exists.

`GET /api/v1/me/courses/{courseId}/progress` returns `lastLectureId` and `lectures`: each live row as `lectureId`, `watched`, `lastOpenedAt`. A student with no rows gets an empty `lectures` and a null `lastLectureId`. A closed course returns `404`.

An allowed `GET` lecture adds `previousLectureId` and `nextLectureId`. Walk the tree depth-first, siblings by `position`, and skip sections. The ends are null. The ids are returned even when that neighbor is locked. Opening it still uses the content gate. This GET does not write progress.

## Slice 9 — Routine

Flyway `V12__course_routine.sql`. Table `course_routine_slots`: course id, `weekday` smallint (`1` Monday through `7` Sunday), `start_time` and `end_time` as time, nullable `place` text, `position` integer, plus the base entity columns.

`PUT /api/v1/courses/{id}/routine` under `COURSE_MANAGE` replaces the whole routine. The body is `slots`: `weekday`, `startTime`, `endTime`, optional `place`. Position follows the array order. An empty `slots` array clears the routine. `GET /api/v1/courses/{id}/routine` under `COURSE_READ` returns those slots.

Only `OFFLINE` and `HYBRID` accept a non-empty routine. `ONLINE`, or a non-empty routine on a course that is not offline or hybrid, returns `409` `ROUTINE_NOT_APPLICABLE` and changes nothing. `endTime` at or before `startTime`, or a weekday outside `1`–`7`, returns `400` `VALIDATION_FAILED`. Two slots on the same weekday whose times overlap return `409` `ROUTINE_OVERLAP`.

`PATCH` of `deliveryMode` to `ONLINE` while any slot exists returns `409` `ROUTINE_NOT_APPLICABLE`. Clear the routine first.

Student course detail includes `routine`: the same slots, in position order. An online course, and an offline or hybrid course with no slots, returns an empty list. A closed course stays `404`.

## Slice 10 — Roster

No new table. `GET /api/v1/courses/{id}/enrollments` under `COURSE_READ` uses `page` and `size` and the shared page envelope.

The default opening is the course's current `opening_number`. Optional query `opening` selects another opening. Rows are enrollments for that course and opening that are not soft-deleted, any status. Each item has `enrollmentId`, `studentId`, `fullName`, `phone`, `source`, `status`, `deliveryMode`, `openingNumber`, and `startedAt`. No password and no payment secret.

A student token returns `403`. A missing course returns `404`.

## Slice 11 — Instructors

Flyway `V13__course_instructors.sql`. Table `course_instructors`: course id, `name` `varchar(500)` not null, nullable `description` text, nullable `photo_file_id` referencing `materials`, `position` integer not null, plus the base entity columns.

An instructor is a profile on the course. It is not a user, not a role, and not a login. There is no `user_id`. The product still has three roles. Name and description are one language, the course's content language.

A course may have several instructors. Order is `position`. A missing position on create appends.

- `POST /api/v1/courses/{id}/instructors` under `COURSE_MANAGE`. `name` is required. `description` is optional.
- `PATCH /api/v1/courses/{id}/instructors/{instructorId}` under `COURSE_MANAGE` may set `name`, `description`, and `position`. A null description clears it. `position` renumbers the course's instructors.
- `POST /api/v1/courses/{id}/instructors/{instructorId}/photo` is multipart under `COURSE_MANAGE`. The file is stored like a cover and `photo_file_id` points at that material. Accept `image/jpeg`, `image/png`, and `image/webp` only. Anything else returns `400` `VALIDATION_FAILED`. A file over `MATERIAL_MAX_BYTES` returns `413` `FILE_TOO_LARGE` and leaves the previous photo in place. Replacing a photo soft-deletes the previous material and removes its bytes.
- `DELETE /api/v1/courses/{id}/instructors/{instructorId}` is a soft delete under `COURSE_DELETE`, audited as `COURSE_INSTRUCTOR_DELETE` with entity type `COURSE_INSTRUCTOR`. The photo bytes are removed. A System Admin delete returns `403`.
- `GET /api/v1/courses/{id}/instructors` under `COURSE_READ` returns `id`, `name`, `description`, `photoFileId`, and `position`, in position order.

The photo is not a course file. It is absent from `courseMaterials`, `sectionMaterials`, and lecture `materials`. Students download it through `GET /api/v1/files/{id}` when the course is published and open, the same way they download a cover. A closed course does not serve the photo. An admin with `COURSE_READ` can download it anyway.

Student course detail includes `instructors` with the same fields. A course with none returns an empty list. A closed course stays `404`.

## Follow-on exit

These checks pass: a material has a title, an optional description, a kind, and an order, and a section can hold one; a batch upload stores every file or none; a lecture has a description and a duration; a student can store a watched flag and a last lecture, and an allowed lecture names the previous and next lecture; an offline or hybrid course stores a routine; an admin can list who is in the current opening; a course can list instructors with a name, a description, and a photo.

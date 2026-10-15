# api-course-depth tests

Follow-on cases. Not part of the course-catalog, content-access, or catalog-read exits. Java `*IT` classes are written during implementation.

Run every class against PostgreSQL. Use Testcontainers in CI. Locally, use `TEST_DB_URL`, `TEST_DB_USER`, and `TEST_DB_PASSWORD` when set. Do not use H2.

Shared setup matches the course-catalog tests: Master Admin, System Admin, a student, `FILE_STORAGE_DIR` on a temp directory, and `MATERIAL_MAX_BYTES` small enough for the rejection case.

Do not add these cases to `CourseContentIT`, `CourseTreeIT`, `ContentAccessIT`, or `CatalogCourseReadIT`.

Write the progress note after the class passes.

## CourseMaterialRecordIT

Slice 6.

- A lecture upload with no title stores the original file name, kind `OTHER`, and the next position. A second file in that lecture gets the following position.
- A course-level upload stores `title`, `description`, and kind `HANDOUT`.
- `POST .../materials/batch` with two files stores both, in order, and returns `201`. One file over the limit returns `413` `FILE_TOO_LARGE` and leaves no new row and no new stored bytes.
- `lectureNodeId` pointing at a section still returns `400` `VALIDATION_FAILED`.
- `sectionNodeId` pointing at a section stores the file on that section. Both node ids, or a lecture id in `sectionNodeId`, returns `400` `VALIDATION_FAILED`.
- PATCH sets title, description, kind, and position on a section file. A null description clears it. A kind outside `HANDOUT`, `EXERCISE`, `SLIDE`, and `OTHER` returns `400` `VALIDATION_FAILED`.
- Marking a section file free on a freemium course succeeds. Clearing that flag returns `409` `FREE_CONTENT_FIXED`. Marking a lecture file free still returns `400` `VALIDATION_FAILED`.
- Replace keeps title, description, kind, position, and `sectionNodeId`, and changes the stored bytes.
- Deleting a section that has a file and no lecture removes that section and that file's stored bytes. Deleting a section that still has a lecture returns `409` `SECTION_NOT_EMPTY` and leaves the file.
- Admin list order inside one lecture is position order.
- Student course detail `courseMaterials` omits section files and the cover, and includes `title`, `kind`, and `position` on a course-level file.
- Student course detail `sectionMaterials` includes the section id, title, and `free`.
- An allowed lecture's `materials` include `title` and `kind`, and still include `materialIds`.
- A student with a current enrollment can download a paid section file. A student without one receives `403` `ENROLLMENT_REQUIRED` on that file and on `GET .../materials/{id}`. A free section file downloads without an enrollment. A closed course returns `404` for the section file.

## LectureSummaryIT

Slice 7.

- PATCH on a lecture stores `description` and `durationMinutes`. Null clears each one.
- `durationMinutes` of `0` returns `400` `VALIDATION_FAILED` and leaves the previous value.
- PATCH of `description` on a section returns `400` `VALIDATION_FAILED`.
- Admin node detail returns both fields.
- Student course detail `contentTree` includes `durationMinutes` on a lecture and does not include `videoRef` or notes.
- An allowed lecture returns `description` and `durationMinutes`. A paid lecture without an enrollment returns `403` `ENROLLMENT_REQUIRED` and does not include them.

## LectureProgressIT

Slice 8.

- A student who may open a lecture can POST progress. The row stores `watched` false when `watched` is omitted, then updates `watched` true and moves `lastOpenedAt` forward.
- A second POST that omits `watched` leaves `watched` true.
- POST for a paid lecture without a current enrollment returns `403` `ENROLLMENT_REQUIRED` and writes no row.
- A full-free course accepts progress with no enrollment.
- A closed course returns `404` and writes no row.
- `GET /api/v1/me/courses` includes `lastLectureId` for the lecture opened last on that course.
- `GET /api/v1/me/courses/{id}/progress` lists `lectureId`, `watched`, and `lastOpenedAt`, and the same `lastLectureId`.
- An allowed lecture in the middle of a depth-first walk returns the previous and next lecture ids. The first lecture has a null previous id. The last has a null next id. A locked neighbor id is still present. GET lecture does not create a progress row.

## CourseRoutineIT

Slice 9.

- PUT on an offline course stores weekday, start, end, place, and position in array order. GET returns that list.
- An empty `slots` array clears the routine.
- PUT on an online course returns `409` `ROUTINE_NOT_APPLICABLE` and stores nothing.
- A hybrid course accepts a routine.
- `endTime` equal to `startTime` returns `400` `VALIDATION_FAILED`.
- Two overlapping slots on the same weekday return `409` `ROUTINE_OVERLAP`.
- PATCH `deliveryMode` to `ONLINE` while a slot exists returns `409` `ROUTINE_NOT_APPLICABLE` and leaves the mode and the slots.
- Student detail of a published open offline course includes `routine` in position order. An online course includes an empty `routine`. A closed course stays `404`.
- A student PUT returns `403`.

## CourseRosterIT

Slice 10.

- After one verified purchase and one offline admission in the current opening, the roster lists both: full name, phone, `source`, `status`, and `openingNumber`. Awaiting-payment is included. An enrollment from the previous opening is absent until `opening` selects that number.
- The list uses the page envelope.
- A student GET returns `403`.
- The payload has no password field.

## CourseInstructorIT

Slice 11.

- Create stores `name` and `description`. A missing name is rejected. A second instructor appends at the next position.
- PATCH updates the name, clears a description with null, and moves `position`.
- A JPEG photo is stored and `photoFileId` is set. A PDF returns `400` `VALIDATION_FAILED` and leaves the instructor without a photo. A photo over the limit returns `413` `FILE_TOO_LARGE` and keeps the previous photo's bytes.
- Replacing a photo hides the previous material and removes its stored bytes.
- The photo id is absent from `courseMaterials`, `sectionMaterials`, and lecture `materials`.
- A student can download the photo of a published open course. A closed course returns `404` on the course detail and does not serve the photo. An admin with `COURSE_READ` can still download it.
- Student course detail `instructors` lists name, description, `photoFileId`, and position. A course with no instructor has an empty list.
- A System Admin delete returns `403`. A Master Admin delete hides the instructor, writes `audit_log` `COURSE_INSTRUCTOR_DELETE`, and removes the photo bytes.
- The instructor payload has no `userId` and no role.

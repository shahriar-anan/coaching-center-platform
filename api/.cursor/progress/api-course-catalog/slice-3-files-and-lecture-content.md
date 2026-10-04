# api-course-catalog — slice 3 (files and lecture content)

## Done

- Flyway `V4__course_files.sql`: `materials`, lecture `video_provider` / `video_ref` / `notes`, `courses.cover_file_id`.
- Local storage (`FILE_STORAGE_DIR`, `MATERIAL_MAX_BYTES`), multipart cover and materials, `GET /api/v1/files/{id}` for admins with `COURSE_READ`.
- YouTube URL parsing, course-level material `free` flag with `FREE_CONTENT_FIXED`.

## Tested

- `CourseContentIT`, `FileStoragePropertiesTest` — passed (local PostgreSQL via `TEST_DB_*`).

## Next

- Slice 4: close and reopen (`CourseCloseIT`).

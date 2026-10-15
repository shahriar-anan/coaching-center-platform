# api-course-catalog — slice 6 (material record)

## Done

- Flyway `V9__material_record.sql`: material title, description, kind, position, and `section_node_id`.
- Single and batch upload. Section files use `sectionNodeId`. `lectureNodeId` still rejects a section.
- PATCH updates title, description, kind, position, and the free flag. Replace keeps those fields. Deleting an empty section removes its files.

## Tested

- `CourseMaterialRecordIT` — passed (local PostgreSQL via `TEST_DB_*`).
- `CourseContentIT`, `CourseTreeIT`, `CatalogCourseReadIT`, `ContentAccessIT` — passed on the same database.

## Next

- Slice 7: lecture description and duration (`LectureSummaryIT`).

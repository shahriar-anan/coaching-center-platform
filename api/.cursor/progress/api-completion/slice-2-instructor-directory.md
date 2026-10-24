# Slice 2 — A2 instructor directory

Done 25 October 2026.

- `GET /api/v1/instructors` (q, status, assigned, page, size; size capped at 100). Master Admin only (`ADMIN_MANAGE`).
- Native query `UserRepository.searchInstructors`, sorted by name. Modules for the page come from one query in `exams.LiveExamInstructorModules`.
- Tests: `InstructorDirectoryIT` (3) and the route list in `AuditLogIT`.
- Result: see the surefire reports. Both green on local PostgreSQL 17.

Next: slice 3, A6 unassign instructor.

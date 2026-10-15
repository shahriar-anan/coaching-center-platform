# api-content-access

Stage plan for who can open lecture content. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Sections 4.1a, 4.4, and 5.4, and Stage 6. This plan adds order only. It does not drop an exit check.

Required cases are in [api-content-access.md](../tests/api-content-access.md).

Start only after `api-payment-access` exits. Do not start `mobile-catalog` until this stage exits.

Admins already read content through the course admin routes. This stage adds the student routes and the file rule. Do not add exams to the tree.

Permission `CONTENT_READ` is granted to `STUDENT`. The service then applies the open-course and enrollment rules. An admin does not need `CONTENT_READ`.

When the slice's checks finish, write `api/.cursor/progress/api-content-access/slice-1-content-gate.md`.

## Slice 1 — Content gate

- `GET /api/v1/catalog/courses/{id}/lectures/{lectureId}` returns `video_ref`, notes, and that lecture's material ids only when the student may open it.
- `GET /api/v1/catalog/courses/{id}/materials/{materialId}` describes a course-level material the student may open.
- `GET /api/v1/files/{id}` returns bytes for a material only under the same rule. Otherwise `403`, code `MATERIAL_FORBIDDEN`. Cover files of listed courses and books stay downloadable as in browse.

A student may open a lecture or material when all of these hold:

- The course is published, not closed, and not soft-deleted.
- And one of: the course is `FULL_FREE`; or the lecture is free; or the course-level material is free; or the student has an `ACTIVE` enrollment whose `opening_number` equals the course's current `opening_number`.

Refusals:

- Awaiting-payment and payment-failed enrollments do not open paid content.
- An active enrollment from an earlier opening does not open paid content after reopen.
- A closed course returns `404` from the student lecture and material routes, matching its absence from the catalog. Free lectures on that course are not served by a side path.
- Paid content without a current enrollment returns `403`, code `ENROLLMENT_REQUIRED`.

`GET /api/v1/me/courses` lists courses for which the caller has an active enrollment on the current opening, plus full-free courses are not listed there unless enrolled. Full-free access does not require a row. The list is only the caller's paid or offline enrollments that still grant access.

## Stage exit

Free lectures, a free course-level material, a full-free course, and paid content with an active enrollment from this opening all open. A student without that enrollment cannot open paid content. A closed course is hidden and serves no content. Reopening does not restore the previous enrollment.

## Follow-on — File names on the student read

Not part of the stage exit above. The catalog course-read follow-on adds file name, content type, and size to a lecture the student may already open, and lists course-level files on the course detail. It does not change who may open a lecture, a course-level material, or `GET /files/{id}`. See [api-catalog-course-read.md](api-catalog-course-read.md).

## Follow-on — Course depth

Not part of the stage exit above. Section files use the same open-course and enrollment rule as course-level files. Progress is recorded only when that rule already allows the lecture. See [api-course-depth.md](api-course-depth.md).

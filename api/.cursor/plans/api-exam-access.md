# api-exam-access

Stage plan for Phase 3 Stage 4. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Sections 5 and 6, and Stage 4. The bundled-access rule is Section 5.2. The mode table is Section 6. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-access.md](../tests/api-exam-access.md). The stage rule is [api-exam-access.mdc](../rules/api-exam-access.mdc).

Start only after `api-exam-components` exits. Do not start `api-exam-purchase` until this stage exits.

This stage enforces the three modes. It does not take a payment. `PURCHASE_REQUIRED` access stays false until the next stage writes an active purchase. Do not add a backdoor grant route.

Package stays `com.coachingcenter.api.exams`. One access predicate is used by the student routes in this stage and by later attempt and upload routes.

When a slice's checks finish, write `api/.cursor/progress/api-exam-access/slice-<number>-<name>.md` before the next slice.

## Locked reading

`ENROLLED_ONLY` follows current course access. A full-free course has no enrollment row in Phase 2, and purchase and offline admission of a full-free course are already rejected. While that course is published and not closed, any logged-in student has access to the linked module. A freemium course grants the module only to a student with an `ACTIVE` enrollment whose `opening_number` equals the course's current `opening_number`. A free lecture on a freemium course does not grant the module. A closed course grants nothing.

## Slice 1 — Access mode and course link

Flyway `V13__exam_access.sql`. Add nullable `access_mode` (`OPEN`, `ENROLLED_ONLY`, `PURCHASE_REQUIRED`) and nullable `course_id` on `exam_modules`. `course_id` references `courses`. Many modules may point at one course. A module has at most one course id.

`PATCH /api/v1/exam-modules/{id}/access` under `EXAM_MANAGE` sets `accessMode` and `courseId` together.

- `OPEN`: `courseId` null, and `price` null. Otherwise `409`, code `ACCESS_MODE_LINK_MISMATCH` when a course id is present, or `409`, code `PRICE_NOT_ALLOWED` when price is not null.
- `PURCHASE_REQUIRED`: `courseId` null, and `price` already set. A missing price returns `409`, code `PRICE_REQUIRED`. A course id returns `ACCESS_MODE_LINK_MISMATCH`.
- `ENROLLED_ONLY`: `courseId` required and `price` null. A missing course id returns `ACCESS_MODE_LINK_MISMATCH`. A missing or soft-deleted course returns `404`. A non-null price returns `PRICE_NOT_ALLOWED`.
- A System Admin still cannot set `price` (`403`). They may set `PURCHASE_REQUIRED` only when a Master Admin has already stored a price.

Admin detail returns `accessMode` and `courseId`. Modules created before this column stay null until this route is called. Admin publish from Stage 1 still works without a mode.

## Slice 2 — Student access gate

Students use `CATALOG_BROWSE`. Do not grant admins a student catalog permission.

- `GET /api/v1/catalog/exam-modules` and `GET /api/v1/catalog/exam-modules/{id}` list published modules only. Draft, archived, and soft-deleted modules are absent. Search matches `title_en` or `title_bn`. A module with a null `access_mode` is absent.
- Detail returns both titles, both root descriptions, price, access mode, `accessGranted`, and the section/paper title tree. It does not return question stems or the correct answer.
- `accessGranted` is true for a published `OPEN` module. A draft or archived module is false, including `OPEN`.
- `accessGranted` for `ENROLLED_ONLY` uses the locked reading above, and the module itself must be published.
- `accessGranted` for `PURCHASE_REQUIRED` is false when that student has no active exam-module purchase, and false when the module is not published. The purchase table arrives in the next stage; until then the predicate is false.
- `GET /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}` requires `accessGranted`. Otherwise `403`, code `EXAM_ACCESS_REQUIRED`. The body returns that component's `description_en` and `description_bn`, its timing fields, `questionCount`, and `maxMarks`. Those rules are the student-facing text in Section 4.3. An MCQ body has no stem, option, `correct`, or `explanation`. A written body includes each question's stem and marks, and still omits `explanation`.
- The component clock is not this predicate. A scheduled window can be `ACTIVE` while `accessGranted` is false.
- Unauthenticated calls return `401`.

## Stage exit

A student's access matches the module's mode: open to any logged-in student, mirroring the linked course's current access, or closed until a direct purchase exists. A mode that disagrees with the course link is rejected.

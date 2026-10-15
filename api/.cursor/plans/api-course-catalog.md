# api-course-catalog

Stage plan for Phase 2 courses. The binding spec is [phase-2-scope.md](../../../docs/phases/phase-2/phase-2-scope.md) Sections 3, 4, and Stage 1. This plan adds order only. It does not drop an exit check.

Required cases are in [api-course-catalog.md](../tests/api-course-catalog.md). The glanceable list is [phase-2-status.md](../../../docs/phases/phase-2/phase-2-status.md).

Start only after Phase 1 `api-identity` is complete. Do not start `api-library-catalog` until this stage's exit passes.

Do not build books, student browse, purchase, payment verification, content gating for students, the mobile UI, web screens, exams, batches, attendance, notices, push, a payment gateway, refunds, cancellation, or Cloudinary. Lectures are YouTube links. Files stay on the local API server.

## Admin create and update sequence

Create and update use this sequence only. A category groups courses. A section is a folder inside one course. Do not store a category as a `SECTION` node.

1. **Course details.** `POST /api/v1/courses` stores both titles, content language, description, delivery mode, access type, and price when the price rule allows it. The course is saved as `DRAFT`. Cover is `POST /api/v1/courses/{id}/cover` in this same step, after the id exists. Update uses `PATCH /api/v1/courses/{id}` for those fields. Access type stays fixed. Replacing the cover uses the same cover route.
2. **Category.** Place the course in the category tree from the follow-on slice below. A main category has no parent. Under a category, subcategories and courses are one ordered list of siblings. A course is not a category and cannot contain categories.
3. **Lectures and materials.** Use the content tree inside that course. A lecture may have no section (`parentId` null) or sit under a section. A section may contain sections or lectures. A lecture cannot contain children. Notes and the YouTube URL are `PATCH` on the lecture. A file for that lecture is `POST /api/v1/courses/{id}/materials` with `lectureNodeId`. A file for the whole course omits `lectureNodeId`.

Update walks the same three steps on the saved course.

Register new permissions from a `PermissionContributor` in the courses feature. Do not edit the permission checker. Only `MASTER_ADMIN` receives a `*_DELETE` permission.

Packages under `com.coachingcenter.api`: `courses` and `files`. Keep each feature's controller, service, repository, and DTOs inside that feature.

When a slice's checks finish, write `api/.cursor/progress/api-course-catalog/slice-<number>-<name>.md` before the next slice.

## Permissions

| Permission | Master Admin | System Admin | Student |
|---|---|---|---|
| `COURSE_READ` | yes | yes | no |
| `COURSE_MANAGE` | yes | yes | no |
| `COURSE_PRICE` | yes | no | no |
| `COURSE_CLOSE` | yes | no | no |
| `COURSE_DELETE` | yes | no | no |

`COURSE_MANAGE` creates and edits titles, content language, description, delivery mode, cover, the tree, YouTube links, notes, and free flags, and moves a course between draft and published. It does not set the price, close, reopen, archive, or delete.

A System Admin request that sets or changes `price` returns `403`. A full-free course has no price. A freemium course cannot be published until the Master Admin has set a price (`409`, code `PRICE_REQUIRED`).

Access type is `FREEMIUM` or `FULL_FREE`, set at creation, then fixed. Changing it returns `409`, code `ACCESS_TYPE_FIXED`.

## Slice 1 — Course record

Flyway `V2__courses.sql`. One `courses` table extending the base entity (`id`, `created_at`, `updated_at`, `deleted_at`).

Columns: `title_en`, `title_bn`, `content_language` (`BN` or `EN`), `description`, `price` numeric nullable, `delivery_mode` (`ONLINE`, `OFFLINE`, `HYBRID`), `access_type`, `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`), `closed` boolean default false, `opening_number` integer default 0.

- `POST /api/v1/courses` and `PATCH /api/v1/courses/{id}` under `COURSE_MANAGE`, with the price rule above.
- `GET /api/v1/courses` and `GET /api/v1/courses/{id}` under `COURSE_READ`. The list uses `page` and `size` and the shared page envelope. Soft-deleted rows are absent.
- `DELETE /api/v1/courses/{id}` is a soft delete under `COURSE_DELETE`. Write `audit_log` with `actor_user_id`, `action` `COURSE_DELETE`, and `entity_id`.
- Archiving is `POST /api/v1/courses/{id}/archive` under `COURSE_DELETE`, audited as `COURSE_ARCHIVE`. It is not a student-facing close.
- Both titles are required. Description is one language. Do not add a second description, and do not add an access-duration or expiry column.

## Slice 2 — Content tree

Flyway `V3__content_nodes.sql`. Table `content_nodes`: course id, nullable parent id, `node_type` (`SECTION` or `LECTURE` only), `title`, `position`, and `free` boolean.

- A section may contain sections or lectures. A lecture is a leaf.
- Lectures may sit directly under the course. The root parent is null.
- `POST /api/v1/courses/{id}/nodes`, `PATCH /api/v1/courses/{id}/nodes/{nodeId}`, and `POST /api/v1/courses/{id}/nodes/reorder` under `COURSE_MANAGE`.
- Reorder and move keep positions explicit among siblings. Moving a node sets its position to the end of the new parent. Reject a cycle, a lecture used as a parent, and any type other than section or lecture (`400`, code `NODE_TYPE_REJECTED`).
- On a freemium course, `free` is the lecture flag. On a full-free course the flag is ignored because everything is free while the course is open. Setting `free` back to false after it was true returns `409`, code `FREE_CONTENT_FIXED`. Adding more free lectures is allowed.
- `DELETE` of a node is `COURSE_DELETE` and soft-deletes that node. A System Admin delete stays `403`. Do not add an exam node.
- A section delete returns `409`, code `SECTION_NOT_EMPTY`, when that section or any subsection under it still has a lecture. Nothing is deleted in that case. A section whose subsections contain no lecture can be deleted, and those empty subsections are soft-deleted with it.
- Deleting a lecture soft-deletes the files attached to that lecture and removes their stored bytes. Course-level files stay.

## Slice 3 — Files and lecture content

Flyway `V4__course_files.sql`. Table `materials`: course id, nullable lecture node id (null means course-level), stored file name, original file name, byte size, content type. Course gains nullable `cover_file_id`.

Local directory from `FILE_STORAGE_DIR`. Do not commit files. Do not map that directory as public static content. Stored names are unguessable. Download goes through the API.

`MATERIAL_MAX_BYTES` defaults to 104857600 (100 MB) and is configurable so tests can use a small cap. The default value is asserted in the test. A larger upload returns `413`, code `FILE_TOO_LARGE`.

- `POST /api/v1/courses/{id}/cover` and `POST /api/v1/courses/{id}/materials` are multipart under `COURSE_MANAGE`. A material points at a lecture or at the course. A section id is rejected (`400`, code `VALIDATION_FAILED`). A file is not moved between a lecture and the course after upload. Course-level materials have their own `free` flag on freemium courses. Once true, it cannot be set back to false (`FREE_CONTENT_FIXED`).
- `PUT /api/v1/courses/{id}/materials/{materialId}` is multipart under `COURSE_MANAGE`. It replaces the stored file and keeps the same id, lecture link, and free flag. A file over the limit returns `413` and leaves the previous file in place. The course cover is not replaced on this route.
- `DELETE /api/v1/courses/{id}/materials/{materialId}` is `COURSE_DELETE`. It soft-deletes that file and removes its stored bytes. A System Admin delete stays `403`. The course cover is not deleted on this route.
- Lecture video: `PATCH` the lecture with a YouTube URL. Accept `youtube.com/watch`, `youtu.be`, and `youtube.com/embed`. Store `video_provider` `YOUTUBE` and `video_ref` as the video id. Anything else returns `400`, code `INVALID_YOUTUBE_URL`. A null or blank `videoUrl` clears `video_provider` and `video_ref`. Notes are one text field in the course language.
- `GET /api/v1/files/{id}` for admins with `COURSE_READ` returns the bytes. Student download rules are a later stage. Do not leave a public URL.

## Slice 4 — Close and fixed access rules

- `POST /api/v1/courses/{id}/close` and `POST /api/v1/courses/{id}/reopen` under `COURSE_CLOSE`.
- Close is allowed on a published course. It sets `closed` true, writes audit `COURSE_CLOSE`, and does not increment `opening_number`. The course stays published so an admin can still read it.
- The first publish sets `opening_number` to 1 and `closed` false.
- Reopen requires a closed published course. It increments `opening_number`, sets `closed` false, and writes audit `COURSE_REOPEN`.
- A closed, draft, or archived course is not in any student query. Student queries do not exist yet; this slice proves the flags the later browse stage must filter on, using the admin read model (`closed`, `status`, `opening_number`).

## Stage exit

A course with a multi-level tree and lecture content can be created, edited, and reordered. Both titles are stored. Content language is Bengali or English. A freemium course can mark any set of lectures free, including none, and can mark a course-level material free. A file over the limit is rejected. Only the Master Admin can close and reopen. The tree rejects anything other than sections and lectures. A full-free course cannot become freemium. A free lecture or free course-level material cannot become paid.

## Follow-on — Course categories

Not part of the stage exit above. The plan, rule, and cases are [api-course-categories.md](api-course-categories.md), [api-course-categories.mdc](../rules/api-course-categories.mdc), and [api-course-categories.md](../tests/api-course-categories.md).

## Follow-on — Course depth

Not part of the stage exit above. Materials gain a title, description, kind, and order, and a section can hold files. Lectures gain a description and a duration. Students can store a last lecture and a watched flag. Offline and hybrid courses gain a routine. Admins can list the current opening. A course can list instructors with a name, a description, and a photo. An instructor is not a login. The plan, rule, and cases are [api-course-depth.md](api-course-depth.md), [api-course-depth.mdc](../rules/api-course-depth.mdc), and [api-course-depth.md](../tests/api-course-depth.md). Web and mobile stay unchanged.

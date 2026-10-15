# api-exam-written

Stage plan for Phase 3 Stage 7. The binding spec is [phase-3-scope.md](../../../docs/phases/phase-3/phase-3-scope.md) Section 10 and Stage 7. This plan adds order only. It does not drop an exit check.

Required cases are in [api-exam-written.md](../tests/api-exam-written.md). The stage rule is [api-exam-written.mdc](../rules/api-exam-written.mdc).

Start only after `api-exam-mcq` exits. Do not start `api-exam-results` until this stage exits.

Do not score the submission, enter feedback, or publish results. Do not auto-submit a written draft at the deadline.

When the slice's checks finish, write `api/.cursor/progress/api-exam-written/slice-1-submission.md`.

## Locked reading

Edits before final submit have no count limit. Accepted uploads are `image/jpeg`, `image/png`, and `image/webp`.

`EXAM_IMAGE_MAX_BYTES` defaults to 15728640 (15 MiB). A configured value of 20971520 (20 MiB) or higher fails startup. It is not `MATERIAL_MAX_BYTES`. Course and book files keep the 100 MB cap.

Before anything is stored, the server applies EXIF orientation, drops metadata, and scales the image so its longest side is at most 1600 pixels. It does not enlarge a smaller image. It then encodes a JPEG at quality 0.72 and stores only that JPEG under `FILE_STORAGE_DIR` with an unguessable name. The original upload is discarded. Stored `content_type` is `image/jpeg`. A file that cannot be decoded returns `400`, code `VALIDATION_FAILED`, and stores nothing.

Do not send exam images through the course material save path. Do not insert course `materials` rows. Do not map the directory as public static content. Do not add a cloud bucket in this stage. The reduced file is the only object a later cloud upload would store.

After `deadlineAt`, a draft cannot be submitted. It stays `DRAFT`. The server clock decides the deadline. Submit does not run by itself at the deadline.

## Slice 1 — Submission

Flyway `V16__written_submissions.sql`.

`written_submissions`: component id, student id, `status` (`DRAFT`, `UNDER_REVIEW`, `EVALUATED`), `submitted_at` nullable, `pass_percent` integer nullable until submit, and the base-entity columns. One non-deleted row per component and student. `UNDER_REVIEW` is Section 10's under-review state. `pass_percent` is copied from the paper at submit. Feedback is the next stage. There is no single marks column on this table.

`written_submission_images`: submission id, stored file name, original file name, stored byte size, content type, `position`. Byte size and content type describe the reduced JPEG.

`written_submission_questions`: submission id, question id, `stem`, `explanation` nullable, `max_marks`, `position`. Written on final submit as a snapshot of the questions attached at that moment, in their current order. Later edits to the paper do not change these rows. `marks_awarded` is added by the results stage. The student submission payload omits `explanation` until the results stage shows it.

Student routes require the Stage 4 access predicate. Failure returns `403`, code `EXAM_ACCESS_REQUIRED`. The component must be the paper's written component.

- `POST /api/v1/catalog/exam-modules/{moduleId}/components/{componentId}/submission/images` multipart, creates the draft on the first image. The component must already have at least one question. Otherwise `409`, code `COMPONENT_EMPTY`, and nothing is stored. The student reads stems and marks from the Stage 4 component route. This route does not return `explanation`.
- `POST .../submission/images/reorder` with the image ids in order.
- `DELETE .../submission/images/{imageId}` soft-deletes that image row and removes its stored bytes.
- These three routes work only while `DRAFT` and only before the deadline. After final submit they return `409`, code `SUBMISSION_LOCKED`. After the deadline they return `409`, code `DEADLINE_PASSED`.
- `POST .../submission/submit` requires at least one image. Zero images returns `400`, code `SUBMISSION_EMPTY`. Success sets `UNDER_REVIEW`, sets `submitted_at` from `Instant.now()`, locks the row, and writes the question snapshot.
- A second submit returns `SUBMISSION_LOCKED`. There is no admin route that sets the status back to `DRAFT`.
- `GET` of the student's submission returns image ids and positions, not a public file URL. `GET /api/v1/exam-files/{id}` returns the bytes to the owning student and to an admin with `EXAM_READ`. Another student receives `403`.
- `GET /api/v1/exam-components/{componentId}/submissions?status=UNDER_REVIEW` under `EXAM_READ` lists pending submissions for both admin roles. The list uses `page` and `size`.
- The owner of an existing `DRAFT` can still upload, reorder, remove, and submit until the deadline if the module is archived afterward. Creating the first image still requires access. Another student receives `403` for the submission and for its files.

An upload over `EXAM_IMAGE_MAX_BYTES` returns `413`, code `FILE_TOO_LARGE`, and stores nothing. A non-image content type returns `400`, code `VALIDATION_FAILED`. A stored image's longest side is at most 1600 pixels, its content type is `image/jpeg`, and its byte size is the reduced file. The default cap test reads the property and asserts 15728640.

## Stage exit

A student can upload and finalize a written submission. After final submit, no student change is possible. Admins can see it as pending evaluation.

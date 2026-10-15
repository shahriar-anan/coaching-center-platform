# api-exam-written tests

Required cases for Phase 3 Stage 7. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a student with access, a written component whose deadline is in the future, at least two written questions on that component, and `FILE_STORAGE_DIR` pointed at a temp directory. One test reads `EXAM_IMAGE_MAX_BYTES` and asserts the default is 15728640. The rejection case sets that property below the fixture size. Course `MATERIAL_MAX_BYTES` stays 104857600.

## ExamWrittenSubmissionIT

Slice 1.

- A student without access receives `EXAM_ACCESS_REQUIRED`.
- Uploading a jpeg, a png, and a webp creates a `DRAFT`. Each stored object is an unguessable name in `FILE_STORAGE_DIR`, content type `image/jpeg`, longest side at most 1600 pixels, and smaller than the source when the source was larger than that. The original bytes are not kept. The directory is not a public static path.
- Reorder changes positions. Removing an image hides that row and deletes its stored bytes. The same draft accepts another upload after a removal. The test repeats add, reorder, and remove more than once.
- A non-image content type returns `VALIDATION_FAILED` and stores nothing. An upload over `EXAM_IMAGE_MAX_BYTES` returns `FILE_TOO_LARGE` and stores nothing. A 15 MiB upload is the largest the default cap allows. A 20 MiB upload is rejected.
- A component with no questions returns `COMPONENT_EMPTY` on upload and stores nothing.
- Submit with no remaining image returns `SUBMISSION_EMPTY`. Submit with at least one image sets `UNDER_REVIEW` and `submitted_at` from the server clock. The snapshot stores each stem, explanation, and max marks, and copies `passPercent`. Editing the live stem or explanation afterward leaves the snapshot unchanged. The student payload omits the explanation.
- After submit, upload, reorder, remove, and a second submit return `SUBMISSION_LOCKED`. The generated OpenAPI document has no route that sets the submission back to `DRAFT`.
- A draft whose component `deadline_at` is in the past returns `DEADLINE_PASSED` on submit and stays `DRAFT`.
- The owning student can download an image through `GET /exam-files/{id}`. Another student receives `403` for the submission and the file. An admin with `EXAM_READ` can list `status=UNDER_REVIEW` and download the image.
- Archiving the module after the first image still lets the owner submit before the deadline. A student with no draft cannot upload to the archived module.
- Submit copies the component's written questions, their max marks, and their order onto `written_submission_questions`. Detaching a question from the paper after submit leaves that snapshot in place. The submission has no marks-awarded value yet.

# Slice 1 — Submission

## Done
- Flyway `V21__written_submissions.sql` for submissions, images, and question snapshots.
- Student catalog routes for image upload/reorder/delete/submit and submission read.
- `GET /api/v1/exam-files/{id}` with JPEG reduction (1600px, quality 0.72) and `EXAM_IMAGE_MAX_BYTES` (default 15728640).
- Admin pending list under `EXAM_READ`.

## Tested
- `ExamWrittenSubmissionIT` — pass (PostgreSQL).

## Next
- `api-exam-results` slice 1.

# api-material-description

Follow-on (not started). Materials today expose file metadata only (`originalFileName`, `contentType`, `byteSize`). Admins and students cannot attach a human-readable label separate from the uploaded file name.

## Goal

Add optional `description` on course materials (course-level and lecture attachments). Surface it in admin material lists and in student catalog read payloads (`courseMaterials`, lecture `materials`).

## Out of scope

- Book cover files (separate entity path).
- Replacing `originalFileName` with description in download headers.

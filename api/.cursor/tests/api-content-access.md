# api-content-access tests

Required cases for Phase 2 Stage 6. Written during implementation as `ContentAccessIT`. PostgreSQL only. No manual check.

Seed a published open freemium course with one free lecture, one paid lecture, one free course-level material, and one paid course-level material. Seed a full-free course. Give student A an active enrollment for the freemium course's current opening. Leave student B with only an account.

## ContentAccessIT

- Student B can open the free lecture, its materials, and the free course-level material, including file bytes.
- Student B cannot open the paid lecture or the paid course-level material (`ENROLLMENT_REQUIRED`) and cannot download those files (`MATERIAL_FORBIDDEN`).
- Student A can open the paid lecture, its notes, its `video_ref`, and the paid course-level material.
- Any authenticated student can open every lecture and material on the full-free course, with no enrollment.
- An `AWAITING_PAYMENT` enrollment does not open paid content. A `PAYMENT_FAILED` enrollment does not either.
- After the Master Admin closes the freemium course, student catalog detail is `404` and the free lecture route is `404`. Student A cannot download the previously paid file.
- After reopen, student A's old enrollment does not open paid content. A new verified purchase or a new offline admission for the new `opening_number` does.
- `GET /me/courses` includes student A's current enrollment and omits the old opening after reopen.
- The content tree still has no exam node.

# api-exam-access tests

Required cases for Phase 3 Stage 4. Java `*IT` classes are written during implementation. PostgreSQL only. No manual check.

Shared setup: a Master Admin, a System Admin, a student, a published open freemium course, a published open full-free course, and a published exam module.

## ExamAccessModeIT

Slice 1.

- `OPEN` with a null course id and a null price is stored.
- `OPEN` with a course id returns `ACCESS_MODE_LINK_MISMATCH`. `OPEN` with a price returns `PRICE_NOT_ALLOWED`.
- `PURCHASE_REQUIRED` with a null course id and a price already set by the Master Admin is stored.
- `PURCHASE_REQUIRED` with a course id returns `ACCESS_MODE_LINK_MISMATCH`. `PURCHASE_REQUIRED` with a null price returns `PRICE_REQUIRED`.
- `ENROLLED_ONLY` with one course id and a null price is stored. A missing course id returns `ACCESS_MODE_LINK_MISMATCH`. An unknown course id returns `404`.
- Two modules may use the same course id. One module stores only one course id.
- A System Admin setting `price` returns `403`. A System Admin can set `ENROLLED_ONLY` when the price is null, and can set `PURCHASE_REQUIRED` when the price is already set.
- Admin detail returns `accessMode` and `courseId`.

## ExamAccessGateIT

Slice 2.

- A published `OPEN` module appears in the student catalog for either title. `accessGranted` is true. A draft module, an archived module, and a published module with a null access mode are absent.
- Catalog detail returns both titles, both root descriptions, the title tree, and no question stem.
- An unauthenticated catalog `GET` returns `401`.
- `ENROLLED_ONLY` on a full-free course that is published and open: `accessGranted` is true for a logged-in student with no enrollment row.
- `ENROLLED_ONLY` on a freemium course: `accessGranted` is true only for an `ACTIVE` enrollment whose `opening_number` equals the course's current number. Awaiting-payment, an earlier opening, and a student who can open a free lecture but has no enrollment are false.
- Closing the linked course makes `accessGranted` false, including for the full-free course.
- A published `PURCHASE_REQUIRED` module is listed, and `accessGranted` is false for a student with no purchase row.
- An archived module, including `OPEN`, has `accessGranted` false and is absent from the catalog. Its MCQ lifecycle still follows the window.
- A student with access can read a component's Bangla and English rules, question count, and maximum. The MCQ body has no stem or option. The written body includes stems and marks, and has no explanation. Without access the same route returns `EXAM_ACCESS_REQUIRED`.

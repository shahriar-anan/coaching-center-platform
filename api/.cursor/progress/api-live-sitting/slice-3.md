# Slice 3 — Exam routine

Done. `GET /api/v1/me/exam-routine` returns the signed-in student's published exams whose live window has not ended, across every marked module, soonest `liveAt` first and then exam id. A row has the exam, its type, both titles, the module, the category path from the root, the start, the end, and the duration. It omits the description, the rules, the questions, and the question PDF.

Reading the routine does not create a participant. A draft, a final-saved unpublished exam, and a published exam in a draft module are absent. A student marked on one module does not see the other. An unmarked student receives an empty page. Master Admin, System Admin, and Instructor receive `403`. An anonymous call receives `401`.

At the end instant the exam leaves the routine. It stays on the module exam list, and a student who never tapped can still retake it. A live participant stays on the routine until the shared end, and the result stays readable after that. A retake stays off the routine. Removing the roster row, or archiving the module or an ancestor category, drops the row and leaves a posted result readable. A client `now` does not move the cutoff. The course routine is unchanged, and no table was added.

`mvn test "-Dtest=LiveSittingIT,AuditLogIT"` on PostgreSQL 17, database `coaching_center_test`. LiveSittingIT: 21 tests, 0 failures. AuditLogIT: 2 tests, 0 failures.

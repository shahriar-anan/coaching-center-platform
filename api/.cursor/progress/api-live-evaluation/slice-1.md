# Slice 1 — Instructor and written marks

Done. Flyway `V28__live_exam_evaluation.sql` adds the `INSTRUCTOR` role, `live_exam_instructors`, and the evaluation columns on `live_exam_participants`. A Master Admin creates an instructor with name, phone, and email. A password on that request is rejected. The response includes one activation code. The instructor activates with phone, code, and a chosen password, then signs in.

One instructor is assigned to a module. A second assignment is rejected. A System Admin receives `403` on assign and on a mark. The instructor opens submissions for the assigned module only. The Master Admin opens any module. A failed-to-submit row is listed and cannot be marked.

A mark stores marks from 0 through the maximum, an optional comment, and one compressed evaluated PDF. The student sees the marks, pass or fail, and the PDF, and does not see the evaluator. The staff payload names who saved it. A later save replaces the PDF, deletes the previous file, and the live rank follows the new mark.

The MCQ live leaderboard is empty one minute after the window and open six minutes after it. It lists live participants only. Equal scores share a rank and the next rank skips. A written board includes a no-submit zero and leaves an unmarked PDF off until a mark is saved.

`mvn test -Dtest=LiveEvaluationIT` on PostgreSQL 17.9, database `coaching_center_test`. Flyway applied V28. LiveEvaluationIT: 6 tests, 0 failures. LiveSittingIT still passes.

This is the last Phase 3 API stage.

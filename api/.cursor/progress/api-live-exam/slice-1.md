# Slice 1 — One-type exams

Done. Flyway `V25__live_exams.sql` adds `live_exams`, `live_exam_questions`, and `live_exam_options`. Papers and components from the earlier model are not used.

An exam is `MCQ` or `WRITTEN`, with Bangla and English name, description, and rules. Create and edit store a draft. Publish requires a final save, a pass percent from 0 through 100, MCQ questions or a written maximum plus question PDF. The MCQ maximum returned is the sum of the question marks. A student reading the exam before the live instant gets the window and no stems. Inside one module, publish is rejected when another published exam ends fewer than five minutes before this one starts. The error names that exam and its window. Another module does not block. After the live instant, question and schedule edits are rejected. After the end, the status is still `PUBLISHED`. Times are stored as instants and shown in `Asia/Dhaka`.

`mvn test -Dtest=LiveExamIT` on PostgreSQL 17.9, database `coaching_center_test`. Flyway applied V25. Tests run: 5. Failures: 0.

Next: `api-live-roster`.

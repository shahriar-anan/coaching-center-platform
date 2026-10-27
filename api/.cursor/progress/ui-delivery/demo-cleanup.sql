-- Removes every row the demo seed (DemoContentSeed) writes, and nothing else. It also removes what the review itself added on
-- demo rows: sittings, orders, grants and notices of the demo students, and anything hanging off the demo exams and packages.
-- Demo ids all share the shape <letter-or-digit>0000000-0000-4000-8000-<12 digits>, so each table is matched by its prefix:
--   4 = the staff account, 5 = the three demo students, 6 = the instructor, 8 = the announcement, a = categories, b = modules,
--   c = orders, d = grants, e = exams, f = questions, 9 = options, 7 = sittings.
-- Demo orders have tran ids starting with DEMO-; real tran ids are 30 hex characters and never match.
-- audit_log is append-only by trigger, so audit rows written during the review stay. They hold no foreign key to the users.
-- The earlier fourteen-exam demo used the same prefixes, so this script also clears it.
-- Run it once, in one transaction, before every re-seed (the seed refuses to write over an older catalog):
--   psql -1 -v ON_ERROR_STOP=1 -f .cursor/progress/ui-delivery/demo-cleanup.sql
-- Stored PDFs for the demo papers and sittings stay on disk under FILE_STORAGE_DIR; the rows that name them are removed here.
-- To remove the files as well, list them first:
--   SELECT question_stored_file_name FROM live_exams WHERE id::text LIKE 'e0000000-0000-4000-8000-%';
--   SELECT answer_stored_file_name, evaluated_stored_file_name FROM live_exam_participants WHERE id::text LIKE '70000000-0000-4000-8000-%';

DELETE FROM student_notices
WHERE user_id::text LIKE '50000000-0000-4000-8000-%'
   OR user_id::text LIKE '40000000-0000-4000-8000-%'
   OR user_id::text LIKE '60000000-0000-4000-8000-%'
   OR announcement_id::text LIKE '80000000-0000-4000-8000-%'
   OR exam_id::text LIKE 'e0000000-0000-4000-8000-%'
   OR module_id::text LIKE 'b0000000-0000-4000-8000-%';

DELETE FROM announcements
WHERE id::text LIKE '80000000-0000-4000-8000-%';

DELETE FROM exam_outbound_mail
WHERE user_id::text LIKE '50000000-0000-4000-8000-%'
   OR user_id::text LIKE '40000000-0000-4000-8000-%'
   OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM refresh_tokens
WHERE user_id::text LIKE '50000000-0000-4000-8000-%'
   OR user_id::text LIKE '40000000-0000-4000-8000-%'
   OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM one_time_codes
WHERE user_id::text LIKE '50000000-0000-4000-8000-%'
   OR user_id::text LIKE '40000000-0000-4000-8000-%'
   OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM live_exam_order_exams
WHERE exam_id::text LIKE 'e0000000-0000-4000-8000-%'
   OR order_id IN (
    SELECT id FROM live_exam_orders
    WHERE tran_id LIKE 'DEMO-%'
       OR student_id::text LIKE '50000000-0000-4000-8000-%'
       OR module_id::text LIKE 'b0000000-0000-4000-8000-%'
       OR exam_id::text LIKE 'e0000000-0000-4000-8000-%'
);

DELETE FROM live_exam_grants
WHERE student_id::text LIKE '50000000-0000-4000-8000-%'
   OR module_id::text LIKE 'b0000000-0000-4000-8000-%'
   OR exam_id::text LIKE 'e0000000-0000-4000-8000-%'
   OR order_id IN (SELECT id FROM live_exam_orders WHERE tran_id LIKE 'DEMO-%');

DELETE FROM live_exam_orders
WHERE tran_id LIKE 'DEMO-%'
   OR student_id::text LIKE '50000000-0000-4000-8000-%'
   OR module_id::text LIKE 'b0000000-0000-4000-8000-%'
   OR exam_id::text LIKE 'e0000000-0000-4000-8000-%';

DELETE FROM live_exam_answers
WHERE participant_id IN (
    SELECT id FROM live_exam_participants
    WHERE exam_id::text LIKE 'e0000000-0000-4000-8000-%'
       OR student_id::text LIKE '50000000-0000-4000-8000-%'
);

DELETE FROM live_exam_participants
WHERE exam_id::text LIKE 'e0000000-0000-4000-8000-%'
   OR student_id::text LIKE '50000000-0000-4000-8000-%';

DELETE FROM live_exam_options
WHERE question_id::text LIKE 'f0000000-0000-4000-8000-%';

DELETE FROM live_exam_questions
WHERE exam_id::text LIKE 'e0000000-0000-4000-8000-%';

DELETE FROM live_exam_roster
WHERE module_id::text LIKE 'b0000000-0000-4000-8000-%'
   OR student_id::text LIKE '50000000-0000-4000-8000-%';

DELETE FROM live_exam_instructors
WHERE module_id::text LIKE 'b0000000-0000-4000-8000-%'
   OR instructor_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM live_exams
WHERE id::text LIKE 'e0000000-0000-4000-8000-%'
   OR module_id::text LIKE 'b0000000-0000-4000-8000-%';

DELETE FROM live_exam_modules
WHERE id::text LIKE 'b0000000-0000-4000-8000-%';

DELETE FROM live_exam_categories
WHERE id::text LIKE 'a0000000-0000-4000-8000-%';

DELETE FROM student_profiles
WHERE user_id::text LIKE '50000000-0000-4000-8000-%';

DELETE FROM admin_profiles
WHERE user_id::text LIKE '40000000-0000-4000-8000-%'
   OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM users
WHERE id::text LIKE '50000000-0000-4000-8000-%'
   OR id::text LIKE '40000000-0000-4000-8000-%'
   OR id::text LIKE '60000000-0000-4000-8000-%';

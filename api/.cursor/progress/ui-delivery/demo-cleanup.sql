-- Removes every row the demo seed (DemoContentSeed) wrote, and nothing else.
-- Demo ids all share the shape <letter-or-digit>0000000-0000-4000-8000-<12 digits>, so each table is matched by its prefix:
--   a = categories, b = modules, e = exams, f = questions, 9 = options, 7 = participants, 5 = demo students, 6 = instructor.
-- Run it once, in one transaction, against the staging database:
--   psql -1 -v ON_ERROR_STOP=1 -f .cursor/progress/ui-delivery/demo-cleanup.sql
-- Stored PDFs for the demo papers stay on disk under FILE_STORAGE_DIR; the rows that name them are removed here.
-- Remove the files by hand if you want the disk clean too: the question_stored_file_name values are listed by
--   SELECT question_stored_file_name FROM live_exams WHERE id::text LIKE 'e0000000-0000-4000-8000-%';
-- before running this script.

DELETE FROM student_notices
WHERE user_id::text LIKE '50000000-0000-4000-8000-%' OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM exam_outbound_mail
WHERE user_id::text LIKE '50000000-0000-4000-8000-%' OR user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM live_exam_grants
WHERE student_id::text LIKE '50000000-0000-4000-8000-%'
   OR module_id::text LIKE 'b0000000-0000-4000-8000-%'
   OR exam_id::text LIKE 'e0000000-0000-4000-8000-%';

DELETE FROM live_exam_orders
WHERE student_id::text LIKE '50000000-0000-4000-8000-%'
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
WHERE user_id::text LIKE '60000000-0000-4000-8000-%';

DELETE FROM users
WHERE id::text LIKE '50000000-0000-4000-8000-%' OR id::text LIKE '60000000-0000-4000-8000-%';

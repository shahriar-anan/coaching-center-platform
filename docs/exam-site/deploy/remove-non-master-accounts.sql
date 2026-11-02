-- Deletes every account except the Master Admin, with everything that belongs to those accounts. Exams, questions, packages,
-- categories and question papers stay. Plan: docs/exam-site/plans/domain-cutover-google.md, phase 6.
-- This is a hard delete. Take a dump first. Run it once, in one transaction, stopping at the first error:
--
--   psql -1 -v ON_ERROR_STOP=1 -d coaching -f remove-non-master-accounts.sql
--
-- Tables of the old course, book and exam-module side are not cleared. A row there stops the script at its foreign key,
-- and nothing is removed. The same goes for content whose required author is one of these accounts.
-- audit_log is append-only by trigger and has no foreign key to users, so its rows stay.
-- Stored answer and marked PDFs stay on disk. List them first:
--
--   SELECT answer_stored_file_name, evaluated_stored_file_name FROM live_exam_participants;

DO $$
BEGIN
	IF (SELECT count(*) FROM users WHERE role = 'MASTER_ADMIN' AND status = 'ACTIVE' AND deleted_at IS NULL) <> 1 THEN
		RAISE EXCEPTION 'expected exactly one active Master Admin';
	END IF;
END $$;

CREATE TEMP TABLE gone ON COMMIT DROP AS
	SELECT id FROM users WHERE NOT (role = 'MASTER_ADMIN' AND status = 'ACTIVE' AND deleted_at IS NULL);
CREATE TEMP TABLE gone_attempts ON COMMIT DROP AS
	SELECT id FROM live_exam_participants WHERE student_id IN (SELECT id FROM gone);
CREATE TEMP TABLE gone_orders ON COMMIT DROP AS
	SELECT id FROM live_exam_orders WHERE student_id IN (SELECT id FROM gone);

DELETE FROM student_notices
WHERE user_id IN (SELECT id FROM gone) OR participant_id IN (SELECT id FROM gone_attempts);
DELETE FROM live_exam_retake_grants
WHERE student_id IN (SELECT id FROM gone)
	OR granted_by IN (SELECT id FROM gone)
	OR used_participant_id IN (SELECT id FROM gone_attempts);
DELETE FROM live_exam_retake_requests WHERE student_id IN (SELECT id FROM gone);
DELETE FROM live_exam_answer_confirmations WHERE participant_id IN (SELECT id FROM gone_attempts);
DELETE FROM live_exam_answers WHERE participant_id IN (SELECT id FROM gone_attempts);
DELETE FROM live_exam_participants WHERE id IN (SELECT id FROM gone_attempts);

DELETE FROM live_exam_grants
WHERE student_id IN (SELECT id FROM gone) OR order_id IN (SELECT id FROM gone_orders);
DELETE FROM live_exam_order_exams WHERE order_id IN (SELECT id FROM gone_orders);
DELETE FROM live_exam_orders WHERE id IN (SELECT id FROM gone_orders);

DELETE FROM live_exam_reviews WHERE student_id IN (SELECT id FROM gone);
DELETE FROM live_exam_roster WHERE student_id IN (SELECT id FROM gone);
DELETE FROM live_exam_instructors WHERE instructor_id IN (SELECT id FROM gone);
DELETE FROM student_favourites WHERE student_id IN (SELECT id FROM gone);
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM gone);
DELETE FROM one_time_codes WHERE user_id IN (SELECT id FROM gone);
DELETE FROM exam_outbound_mail WHERE user_id IN (SELECT id FROM gone);
DELETE FROM student_profiles WHERE user_id IN (SELECT id FROM gone);
DELETE FROM admin_profiles WHERE user_id IN (SELECT id FROM gone);

UPDATE live_exams SET instructor_id = NULL WHERE instructor_id IN (SELECT id FROM gone);
UPDATE live_exams SET answers_released_by = NULL WHERE answers_released_by IN (SELECT id FROM gone);
UPDATE live_exam_question_papers SET created_by = NULL WHERE created_by IN (SELECT id FROM gone);
UPDATE announcements SET withdrawn_by = NULL WHERE withdrawn_by IN (SELECT id FROM gone);
UPDATE live_exam_retake_requests SET decided_by = NULL WHERE decided_by IN (SELECT id FROM gone);
UPDATE one_time_codes SET created_by = NULL WHERE created_by IN (SELECT id FROM gone);
UPDATE users SET created_by = NULL WHERE created_by IN (SELECT id FROM gone);

DELETE FROM users WHERE id IN (SELECT id FROM gone);

DO $$
BEGIN
	IF (SELECT count(*) FROM users) <> 1 THEN
		RAISE EXCEPTION 'expected one account left';
	END IF;
END $$;

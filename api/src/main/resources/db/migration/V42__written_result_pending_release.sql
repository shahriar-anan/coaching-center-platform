-- A correction to a released written result waits here until Master Admin releases it. The released
-- columns (score, passed, comment, evaluated_*) keep showing the students the released result meanwhile.
ALTER TABLE live_exam_participants ADD COLUMN pending_score integer;
ALTER TABLE live_exam_participants ADD COLUMN pending_passed boolean;
ALTER TABLE live_exam_participants ADD COLUMN pending_comment text;
ALTER TABLE live_exam_participants ADD COLUMN pending_evaluated_by uuid;
ALTER TABLE live_exam_participants ADD COLUMN pending_evaluated_at timestamptz;
ALTER TABLE live_exam_participants ADD COLUMN pending_stored_file_name varchar(255);
ALTER TABLE live_exam_participants ADD COLUMN pending_original_file_name varchar(500);
ALTER TABLE live_exam_participants ADD COLUMN pending_byte_size bigint;
ALTER TABLE live_exam_participants ADD COLUMN pending_content_type varchar(255);

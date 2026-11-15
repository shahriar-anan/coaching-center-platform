-- Written answer scripts are kept on the server as a draft until the student submits, or the sitting's time ends
-- (docs/exam-site/written-draft-submission-plan.md). The draft becomes the answer script at submit or at the deadline.
ALTER TABLE live_exam_participants ADD COLUMN draft_stored_file_name VARCHAR(255);
ALTER TABLE live_exam_participants ADD COLUMN draft_original_file_name VARCHAR(500);
ALTER TABLE live_exam_participants ADD COLUMN draft_byte_size BIGINT;
ALTER TABLE live_exam_participants ADD COLUMN draft_uploaded_at TIMESTAMPTZ;

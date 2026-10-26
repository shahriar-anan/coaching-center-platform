-- Notices for the phase 2 kinds (plan section 6.2), module-level notices, and announcements that have no exam (approved for item 9).
-- Every kind that existed before stays valid; REMINDER and MCQ_RESULT never reached a row.
ALTER TABLE student_notices DROP CONSTRAINT student_notices_kind_check;
ALTER TABLE student_notices ADD CONSTRAINT student_notices_kind_check CHECK (
    kind IN (
        'EXAM_REMINDER', 'EXAM_STARTING_SOON', 'EXAM_OPEN', 'PURCHASE_GRANTED', 'EXAM_PUBLISHED',
        'RETAKE_AVAILABLE', 'ANNOUNCEMENT', 'MCQ_SCORE', 'WRITTEN_RESULT'
    )
);

-- An announcement has no exam. A module-level notice names its module instead.
ALTER TABLE student_notices ALTER COLUMN exam_id DROP NOT NULL;
ALTER TABLE student_notices ADD COLUMN module_id UUID NULL REFERENCES live_exam_modules (id);

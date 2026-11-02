-- A manual grant by the centre is its own notice, so it does not offer a payment. Every earlier kind stays valid.
ALTER TABLE student_notices DROP CONSTRAINT student_notices_kind_check;
ALTER TABLE student_notices ADD CONSTRAINT student_notices_kind_check CHECK (
    kind IN (
        'EXAM_REMINDER', 'EXAM_STARTING_SOON', 'EXAM_OPEN', 'PURCHASE_GRANTED', 'EXAM_PUBLISHED',
        'RETAKE_AVAILABLE', 'ANNOUNCEMENT', 'MCQ_SCORE', 'WRITTEN_RESULT', 'EXAM_UPDATED',
        'EXAM_TIME_CHANGED', 'ACCESS_GRANTED'
    )
);

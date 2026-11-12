-- Offline admission, part 4 (plan sections 4.4 and 7): the course-end cap on a grant, the OFFLINE grant source, and every
-- notice and mail kind the feature sends. Each check constraint is re-created from its last definition, so no kind that
-- exists today is lost:
--   live_exam_grants_source_check, last defined in V39: ORDER, MANUAL, BUNDLE
--   student_notices_kind_check, last defined in V80 (18 kinds, copied below)
--   exam_outbound_mail_kind_check, last defined in V80 (10 kinds, copied below)

-- A grant's own end. Null for every grant that exists today, so nothing changes for them. An OFFLINE grant ends here.
ALTER TABLE live_exam_grants ADD COLUMN access_ends_at TIMESTAMPTZ NULL;

ALTER TABLE live_exam_grants DROP CONSTRAINT live_exam_grants_source_check;
ALTER TABLE live_exam_grants ADD CONSTRAINT live_exam_grants_source_check CHECK (
    source IN ('ORDER', 'MANUAL', 'BUNDLE', 'OFFLINE')
);

ALTER TABLE student_notices DROP CONSTRAINT student_notices_kind_check;
ALTER TABLE student_notices ADD CONSTRAINT student_notices_kind_check CHECK (
    kind IN (
        'EXAM_REMINDER', 'EXAM_STARTING_SOON', 'EXAM_OPEN', 'PURCHASE_GRANTED', 'EXAM_PUBLISHED',
        'RETAKE_AVAILABLE', 'ANNOUNCEMENT', 'MCQ_SCORE', 'WRITTEN_RESULT', 'EXAM_UPDATED',
        'EXAM_TIME_CHANGED', 'ACCESS_GRANTED', 'RETAKE_DECLINED',
        'INSTALLMENT_PAYMENT_RECEIVED', 'INSTALLMENT_DUE_SOON', 'INSTALLMENT_OVERDUE',
        'INSTALLMENT_PAYMENT_VOIDED', 'INSTALLMENT_SCHEDULE_CHANGED',
        'ADMISSION_CHANGES_REQUESTED', 'ADMISSION_APPROVED', 'ADMISSION_UPDATED', 'ADMISSION_CLOSED',
        'OFFLINE_FEE_RECEIVED', 'OFFLINE_FEE_VOIDED', 'OFFLINE_FEE_DUE_SOON', 'OFFLINE_FEE_DUE_TODAY',
        'OFFLINE_FEE_OVERDUE', 'BATCH_ROUTINE_UPDATED'
    )
);

ALTER TABLE exam_outbound_mail DROP CONSTRAINT exam_outbound_mail_kind_check;
ALTER TABLE exam_outbound_mail ADD CONSTRAINT exam_outbound_mail_kind_check CHECK (
    kind IN (
        'PAYMENT_RECEIPT', 'EXAM_TIME', 'HOUR_BEFORE', 'REFUND', 'PURCHASE_NOTICE',
        'INSTALLMENT_RECEIPT', 'INSTALLMENT_DUE_SOON', 'INSTALLMENT_OVERDUE',
        'INSTALLMENT_PAYMENT_VOIDED', 'INSTALLMENT_SCHEDULE_CHANGED',
        'ADMISSION_APPROVED', 'ADMISSION_CHANGES_REQUESTED', 'ADMISSION_UPDATED', 'ADMISSION_CLOSED',
        'OFFLINE_FEE_RECEIPT', 'OFFLINE_FEE_DUE_SOON', 'OFFLINE_FEE_DUE_TODAY', 'OFFLINE_FEE_OVERDUE'
    )
);

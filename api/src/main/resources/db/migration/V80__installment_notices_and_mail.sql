-- Notice and mail kinds for direct enrolment installments, and the two links a notice needs (plan section 5).
-- Each check constraint is re-created from its last definition in the migrations, so no kind that exists today is lost:
--   student_notices_kind_check, last defined in V60 (13 kinds, copied here):
--     EXAM_REMINDER, EXAM_STARTING_SOON, EXAM_OPEN, PURCHASE_GRANTED, EXAM_PUBLISHED, RETAKE_AVAILABLE, ANNOUNCEMENT,
--     MCQ_SCORE, WRITTEN_RESULT, EXAM_UPDATED, EXAM_TIME_CHANGED, ACCESS_GRANTED, RETAKE_DECLINED
--   exam_outbound_mail_kind_check, last defined in V38 (5 kinds, copied here):
--     PAYMENT_RECEIPT, EXAM_TIME, HOUR_BEFORE, REFUND, PURCHASE_NOTICE
-- Only the new kinds are added after them.

ALTER TABLE student_notices DROP CONSTRAINT student_notices_kind_check;
ALTER TABLE student_notices ADD CONSTRAINT student_notices_kind_check CHECK (
    kind IN (
        'EXAM_REMINDER', 'EXAM_STARTING_SOON', 'EXAM_OPEN', 'PURCHASE_GRANTED', 'EXAM_PUBLISHED',
        'RETAKE_AVAILABLE', 'ANNOUNCEMENT', 'MCQ_SCORE', 'WRITTEN_RESULT', 'EXAM_UPDATED',
        'EXAM_TIME_CHANGED', 'ACCESS_GRANTED', 'RETAKE_DECLINED',
        'INSTALLMENT_PAYMENT_RECEIVED', 'INSTALLMENT_DUE_SOON', 'INSTALLMENT_OVERDUE',
        'INSTALLMENT_PAYMENT_VOIDED', 'INSTALLMENT_SCHEDULE_CHANGED'
    )
);

ALTER TABLE exam_outbound_mail DROP CONSTRAINT exam_outbound_mail_kind_check;
ALTER TABLE exam_outbound_mail ADD CONSTRAINT exam_outbound_mail_kind_check CHECK (
    kind IN (
        'PAYMENT_RECEIPT', 'EXAM_TIME', 'HOUR_BEFORE', 'REFUND', 'PURCHASE_NOTICE',
        'INSTALLMENT_RECEIPT', 'INSTALLMENT_DUE_SOON', 'INSTALLMENT_OVERDUE',
        'INSTALLMENT_PAYMENT_VOIDED', 'INSTALLMENT_SCHEDULE_CHANGED'
    )
);

-- A notice about an installment names the installment or the payment, not an exam or package, so a later change to
-- those cannot hide it (plan section 7). Deleting an unpaid installment clears the link, and the inbox then hides the notice.
ALTER TABLE student_notices
    ADD COLUMN installment_id UUID NULL REFERENCES live_exam_installments (id) ON DELETE SET NULL,
    ADD COLUMN direct_payment_id UUID NULL REFERENCES live_exam_direct_payments (id);

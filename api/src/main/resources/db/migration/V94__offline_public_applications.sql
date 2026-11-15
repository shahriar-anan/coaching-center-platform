-- Public offline applications (docs/offline-admission/public-admission-plan.md, sections 4, 5 and 9; P10, P11, Q3, Q4).
-- An application can come from someone with no account yet (source PUBLIC): its student_id stays empty until onboarding makes or
-- matches the account. The join code (an account that already exists joins the batch), the reopen link (changes requested) and the
-- application token of a public applicant are kept as hashes only. Additive except for the two nullable changes and the index swap.

ALTER TABLE offline_admissions ALTER COLUMN student_id DROP NOT NULL;
ALTER TABLE offline_admission_submissions ALTER COLUMN submitted_by DROP NOT NULL;
ALTER TABLE offline_admissions ALTER COLUMN created_by DROP NOT NULL;

ALTER TABLE offline_admissions
    ADD COLUMN source                  VARCHAR(8) NOT NULL DEFAULT 'DESK',
    ADD COLUMN access_token_hash       VARCHAR(255) NULL,
    ADD COLUMN join_code_hash          VARCHAR(255) NULL,
    ADD COLUMN join_code_expires_at    TIMESTAMPTZ NULL,
    ADD COLUMN join_code_sent_at       TIMESTAMPTZ NULL,
    ADD COLUMN join_code_sent_by       UUID NULL REFERENCES users (id),
    ADD COLUMN join_code_used_at       TIMESTAMPTZ NULL,
    ADD COLUMN join_code_failures      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reopen_token_hash       VARCHAR(255) NULL,
    ADD COLUMN reopen_token_expires_at TIMESTAMPTZ NULL,
    ADD COLUMN matched_user_id         UUID NULL REFERENCES users (id),
    ADD CONSTRAINT offline_admissions_source_check CHECK (source IN ('PUBLIC', 'ACCOUNT', 'DESK')),
    ADD CONSTRAINT offline_admissions_account_check CHECK (student_id IS NOT NULL OR source = 'PUBLIC'),
    ADD CONSTRAINT offline_admissions_join_failures_check CHECK (join_code_failures >= 0);

-- One open admission per student and batch (P11). A student with an open admission that has no batch yet keeps the old rule: one.
DROP INDEX offline_admissions_one_open_per_student;

CREATE UNIQUE INDEX offline_admissions_one_open_per_student_batch
    ON offline_admissions (student_id, batch_id)
    WHERE status <> 'CLOSED' AND student_id IS NOT NULL AND batch_id IS NOT NULL;

CREATE UNIQUE INDEX offline_admissions_one_open_without_batch
    ON offline_admissions (student_id)
    WHERE status <> 'CLOSED' AND student_id IS NOT NULL AND batch_id IS NULL;

-- One open public application per email and batch, while the applicant has no account (the same email again opens that one).
CREATE UNIQUE INDEX offline_admissions_open_public_email
    ON offline_admissions (lower(email), batch_id)
    WHERE student_id IS NULL AND status <> 'CLOSED';

-- The in-app notice that tells an account holder the join code is ready (plan section 7). The kinds are the V84 list plus this one.
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
        'OFFLINE_FEE_OVERDUE', 'BATCH_ROUTINE_UPDATED', 'ADMISSION_JOIN_CODE', 'ADMISSION_BATCH_ADDED'
    )
);

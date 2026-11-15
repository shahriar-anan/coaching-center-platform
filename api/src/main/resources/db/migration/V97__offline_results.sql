-- Offline exam results (docs/offline-admission/offline-results-plan.md, section 3; the contract is results-api-contract.md).
-- New tables only, plus the two notice kinds. Nothing existing is changed except the notice kind check, which is re-created from its
-- last definition (V94) with the two kinds added, so no kind that exists today is lost.

-- One row per uploaded result sheet of an exam class. A STAGED sheet waits for the Master Admin; a PUBLISHED one is what students see.
CREATE TABLE offline_result_sheets (
    id               UUID PRIMARY KEY,
    class_id         UUID NOT NULL REFERENCES offline_classes (id),
    batch_id         UUID NOT NULL REFERENCES offline_batches (id),
    status           VARCHAR(12) NOT NULL,
    exam_type        VARCHAR(16) NOT NULL,
    full_marks       NUMERIC(8,2) NOT NULL,
    negative         NUMERIC(8,2) NOT NULL,
    pass_mark        NUMERIC(8,2) NULL,
    file_name        VARCHAR(500) NOT NULL,
    content_type     VARCHAR(255) NULL,
    byte_size        BIGINT NOT NULL,
    stored_name      VARCHAR(255) NOT NULL,
    uploaded_by      UUID NULL REFERENCES users (id),
    uploaded_at      TIMESTAMPTZ NOT NULL,
    published_by     UUID NULL REFERENCES users (id),
    published_at     TIMESTAMPTZ NULL,
    CONSTRAINT offline_result_sheets_status_check CHECK (status IN ('STAGED', 'PUBLISHED', 'REPLACED')),
    CONSTRAINT offline_result_sheets_exam_type_check CHECK (exam_type IN ('MCQ', 'MCQ_WRITTEN', 'WRITTEN')),
    CONSTRAINT offline_result_sheets_full_marks_check CHECK (full_marks > 0),
    CONSTRAINT offline_result_sheets_negative_check CHECK (negative >= 0),
    CONSTRAINT offline_result_sheets_pass_mark_check CHECK (pass_mark IS NULL OR pass_mark >= 0),
    CONSTRAINT offline_result_sheets_byte_size_check CHECK (byte_size >= 0)
);

-- At most one published and at most one staged sheet per exam class.
CREATE UNIQUE INDEX offline_result_sheets_published_key ON offline_result_sheets (class_id) WHERE status = 'PUBLISHED';
CREATE UNIQUE INDEX offline_result_sheets_staged_key ON offline_result_sheets (class_id) WHERE status = 'STAGED';
CREATE INDEX offline_result_sheets_batch_idx ON offline_result_sheets (batch_id);

-- One row per student of a sheet: a row of the file (matched or not), or an expected student with no row (absent, no marks).
CREATE TABLE offline_result_rows (
    id              UUID PRIMARY KEY,
    sheet_id        UUID NOT NULL REFERENCES offline_result_sheets (id) ON DELETE CASCADE,
    admission_id    UUID NULL REFERENCES offline_admissions (id),
    roll_no         VARCHAR(12) NULL,
    absent          BOOLEAN NOT NULL DEFAULT FALSE,
    right_count     INTEGER NULL,
    wrong_count     INTEGER NULL,
    mcq             NUMERIC(10,2) NULL,
    written         NUMERIC(10,2) NULL,
    total           NUMERIC(10,2) NULL,
    rank            INTEGER NULL,
    passed          BOOLEAN NULL,
    file_total      NUMERIC(10,2) NULL,
    file_rank       INTEGER NULL,
    row_number      INTEGER NULL,
    CONSTRAINT offline_result_rows_right_check CHECK (right_count IS NULL OR right_count >= 0),
    CONSTRAINT offline_result_rows_wrong_check CHECK (wrong_count IS NULL OR wrong_count >= 0),
    CONSTRAINT offline_result_rows_rank_check CHECK (rank IS NULL OR rank >= 1),
    CONSTRAINT offline_result_rows_absent_check CHECK (NOT absent OR (total IS NULL AND rank IS NULL AND passed IS NULL))
);

CREATE INDEX offline_result_rows_sheet_idx ON offline_result_rows (sheet_id);
CREATE INDEX offline_result_rows_admission_idx ON offline_result_rows (admission_id);

-- The two notice kinds of a published result (V94 list plus these two).
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
        'OFFLINE_FEE_OVERDUE', 'BATCH_ROUTINE_UPDATED', 'ADMISSION_JOIN_CODE', 'ADMISSION_BATCH_ADDED',
        'OFFLINE_RESULT_PUBLISHED', 'OFFLINE_RESULT_UPDATED'
    )
);

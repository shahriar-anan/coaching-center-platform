-- Offline routines and classes (plan docs/offline-admission/offline-instructor-pay-plan.md, sections 4.2 and 9.2).
-- New tables only, plus the copy of each batch's old single routine into the new table. The old routine columns on offline_batches
-- stay as they are.

-- One row per monthly routine upload. Only one CONFIRMED routine per batch and month is live (the partial index below).
CREATE TABLE offline_batch_routines (
    id                    UUID PRIMARY KEY,
    batch_id              UUID NOT NULL REFERENCES offline_batches (id),
    month                 DATE NOT NULL,
    original_file         VARCHAR(255) NULL,
    original_name         VARCHAR(500) NULL,
    original_byte_size    BIGINT NULL,
    student_file          VARCHAR(255) NULL,
    student_byte_size     BIGINT NULL,
    version               INTEGER NOT NULL,
    status                VARCHAR(16) NOT NULL,
    legacy_original       BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by           UUID NULL REFERENCES users (id),
    uploaded_at           TIMESTAMPTZ NOT NULL,
    confirmed_by          UUID NULL REFERENCES users (id),
    confirmed_at          TIMESTAMPTZ NULL,
    parse_report          JSONB NULL,
    CONSTRAINT offline_batch_routines_month_check CHECK (EXTRACT(DAY FROM month) = 1),
    CONSTRAINT offline_batch_routines_version_check CHECK (version >= 1),
    CONSTRAINT offline_batch_routines_status_check CHECK (status IN ('PREVIEW', 'CONFIRMED', 'SUPERSEDED'))
);

CREATE UNIQUE INDEX offline_batch_routines_confirmed_key ON offline_batch_routines (batch_id, month) WHERE status = 'CONFIRMED';

-- One row per class in a routine. amount is the due amount: the override when there is one, else the batch rate of the instructor.
-- The amount is kept on the row (not computed) so a rate change can update the unsettled classes in one statement.
CREATE TABLE offline_classes (
    id               UUID PRIMARY KEY,
    batch_id         UUID NOT NULL REFERENCES offline_batches (id),
    routine_id       UUID NULL REFERENCES offline_batch_routines (id),
    held_on          DATE NOT NULL,
    starts_at        TIME NULL,
    ends_at          TIME NULL,
    subject          VARCHAR(200) NOT NULL,
    topic            VARCHAR(500) NULL,
    kind             VARCHAR(16) NOT NULL,
    instructor_id    UUID NULL REFERENCES offline_instructors (id),
    instructor_text  VARCHAR(200) NULL,
    amount           NUMERIC(12,2) NULL,
    rate_override    NUMERIC(12,2) NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    status_by        UUID NULL REFERENCES users (id),
    status_at        TIMESTAMPTZ NULL,
    cancelled_at     TIMESTAMPTZ NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_classes_kind_check CHECK (kind IN ('LECTURE', 'EXAM', 'OTHER')),
    CONSTRAINT offline_classes_status_check CHECK (status IN ('SCHEDULED', 'PAID_PENDING', 'PAID', 'NOT_TAKEN_PENDING', 'NOT_TAKEN',
        'CANCELLED')),
    CONSTRAINT offline_classes_amount_check CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT offline_classes_override_check CHECK (rate_override IS NULL OR rate_override >= 0)
);

CREATE INDEX offline_classes_batch_held_idx ON offline_classes (batch_id, held_on);
CREATE INDEX offline_classes_instructor_idx ON offline_classes (instructor_id);

-- The old single routine (V81, V86) becomes a confirmed routine for the month it was uploaded (Dhaka time). It has no classes and no
-- student copy, and legacy_original marks it staff only: the student route never serves it (plan side effects 1 and 2).
-- LEGACY_ROUTINE_COPY_BEGIN
INSERT INTO offline_batch_routines (id, batch_id, month, original_file, original_name, original_byte_size, student_file,
    student_byte_size, version, status, legacy_original, uploaded_by, uploaded_at, confirmed_by, confirmed_at, parse_report)
SELECT gen_random_uuid(), b.id,
    date_trunc('month', COALESCE(b.routine_uploaded_at, b.created_at) AT TIME ZONE 'Asia/Dhaka')::date,
    b.routine_stored_name, b.routine_original_name, b.routine_byte_size, NULL, NULL,
    1, 'CONFIRMED', TRUE, b.routine_uploaded_by, COALESCE(b.routine_uploaded_at, b.created_at),
    b.routine_uploaded_by, COALESCE(b.routine_uploaded_at, b.created_at), NULL
FROM offline_batches b
WHERE b.routine_stored_name IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM offline_batch_routines r WHERE r.batch_id = b.id AND r.legacy_original);
-- LEGACY_ROUTINE_COPY_END

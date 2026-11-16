-- The instructor's own page (17 Nov). A class added there can be kept out of the batch's routine: it is then only a row of the
-- instructor's pay, never shown to students and never touched by a routine upload. Payments to an instructor for something that is not
-- a class (travel, a bonus, an advance) are kept in their own table, with the reason the admin typed; a staff entry waits for the
-- Master Admin, as class payments do.
ALTER TABLE offline_classes ADD COLUMN in_routine BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE offline_instructor_other_payments (
    id               UUID PRIMARY KEY,
    instructor_id    UUID NOT NULL REFERENCES offline_instructors (id),
    batch_id         UUID NULL REFERENCES offline_batches (id),
    reason           VARCHAR(500) NOT NULL,
    amount           NUMERIC(12,2) NOT NULL,
    method           VARCHAR(16) NOT NULL,
    reference        VARCHAR(100) NULL,
    paid_on          DATE NOT NULL,
    note             VARCHAR(500) NULL,
    status           VARCHAR(16) NOT NULL,
    recorded_by      UUID NOT NULL REFERENCES users (id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    decided_by       UUID NULL REFERENCES users (id),
    decided_at       TIMESTAMPTZ NULL,
    decision_reason  VARCHAR(500) NULL,
    voided_by        UUID NULL REFERENCES users (id),
    voided_at        TIMESTAMPTZ NULL,
    void_reason      VARCHAR(500) NULL,
    corrected_at     TIMESTAMPTZ NULL,
    CONSTRAINT offline_instructor_other_payments_amount_check CHECK (amount > 0),
    CONSTRAINT offline_instructor_other_payments_method_check CHECK (method IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')),
    CONSTRAINT offline_instructor_other_payments_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'VOIDED'))
);

CREATE INDEX offline_instructor_other_payments_instructor_idx ON offline_instructor_other_payments (instructor_id, paid_on DESC);
CREATE INDEX offline_instructor_other_payments_status_idx ON offline_instructor_other_payments (status);

-- The payment records page corrects these too (kind OTHER).
ALTER TABLE payment_edits DROP CONSTRAINT payment_edits_kind_check;
ALTER TABLE payment_edits ADD CONSTRAINT payment_edits_kind_check CHECK (kind IN ('FEE', 'CLASS', 'DIRECT', 'OTHER'));

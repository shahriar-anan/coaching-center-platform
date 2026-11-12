-- Offline admissions: monthly fee dues, payments taken in person, and how each payment is applied to the dues (plan docs/offline-admission).
-- A payment is applied to dues through offline_fee_allocations; a voided payment stays in the table and its receipt number is never reused.

-- The monthly schedule. seq 1 is the first month. A cancelled due stays in the table and no longer counts.
CREATE TABLE offline_fee_dues (
    id            UUID PRIMARY KEY,
    admission_id  UUID NOT NULL REFERENCES offline_admissions (id),
    seq           SMALLINT NOT NULL,
    due_on        DATE NOT NULL,
    amount        NUMERIC(12,2) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    cancelled_at  TIMESTAMPTZ NULL,
    CONSTRAINT offline_fee_dues_amount_check CHECK (amount >= 0),
    CONSTRAINT offline_fee_dues_admission_seq_key UNIQUE (admission_id, seq)
);

-- Every payment taken from a student in person. Status moves PENDING -> APPROVED or REJECTED, and APPROVED -> VOIDED.
CREATE TABLE offline_fee_payments (
    id               UUID PRIMARY KEY,
    admission_id     UUID NOT NULL REFERENCES offline_admissions (id),
    amount           NUMERIC(12,2) NOT NULL,
    method           VARCHAR(16) NOT NULL,
    reference        VARCHAR(100) NOT NULL,
    paid_on          DATE NOT NULL,
    note             VARCHAR(500) NULL,
    status           VARCHAR(16) NOT NULL,
    recorded_by      UUID NOT NULL REFERENCES users (id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    decided_by       UUID NULL REFERENCES users (id),
    decided_at       TIMESTAMPTZ NULL,
    decision_reason  VARCHAR(500) NULL,
    receipt_no       VARCHAR(24) NULL UNIQUE,
    voided_by        UUID NULL REFERENCES users (id),
    voided_at        TIMESTAMPTZ NULL,
    void_reason      VARCHAR(500) NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_fee_payments_amount_check CHECK (amount > 0),
    CONSTRAINT offline_fee_payments_method_check CHECK (method IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')),
    CONSTRAINT offline_fee_payments_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'VOIDED'))
);

-- How much of a payment goes to one due. The sum for a payment never exceeds the payment amount (checked by the application).
CREATE TABLE offline_fee_allocations (
    id          UUID PRIMARY KEY,
    payment_id  UUID NOT NULL REFERENCES offline_fee_payments (id),
    due_id      UUID NOT NULL REFERENCES offline_fee_dues (id),
    amount      NUMERIC(12,2) NOT NULL,
    CONSTRAINT offline_fee_allocations_amount_check CHECK (amount > 0)
);

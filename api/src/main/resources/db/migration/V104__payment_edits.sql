-- Corrections to recorded payments (payment-records-edit-plan.md, 15 Nov). A System Admin's edit waits for the Master Admin, who
-- finalizes or rejects it; the Master Admin's own edit is final at once. Each edit keeps the payment's values before and after.
-- kind: FEE (offline_fee_payments), CLASS (offline_class_payments), DIRECT (live_exam_direct_payments).
CREATE TABLE payment_edits (
    id               UUID PRIMARY KEY,
    kind             VARCHAR(16) NOT NULL,
    payment_id       UUID NOT NULL,
    status           VARCHAR(16) NOT NULL,
    before_values    JSONB NOT NULL,
    after_values     JSONB NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    requested_by     UUID NOT NULL REFERENCES users (id),
    requested_at     TIMESTAMPTZ NOT NULL,
    decided_by       UUID NULL REFERENCES users (id),
    decided_at       TIMESTAMPTZ NULL,
    decision_reason  VARCHAR(500) NULL,
    CONSTRAINT payment_edits_kind_check CHECK (kind IN ('FEE', 'CLASS', 'DIRECT')),
    CONSTRAINT payment_edits_status_check CHECK (status IN ('PENDING', 'FINALIZED', 'REJECTED'))
);

-- One waiting edit per payment.
CREATE UNIQUE INDEX payment_edits_one_pending ON payment_edits (kind, payment_id) WHERE status = 'PENDING';
CREATE INDEX payment_edits_payment_idx ON payment_edits (kind, payment_id, requested_at DESC);

-- When a finalized edit last changed the payment. A fee receipt says "Corrected on" this date.
ALTER TABLE offline_fee_payments ADD COLUMN corrected_at TIMESTAMPTZ NULL;
ALTER TABLE offline_class_payments ADD COLUMN corrected_at TIMESTAMPTZ NULL;
ALTER TABLE live_exam_direct_payments ADD COLUMN corrected_at TIMESTAMPTZ NULL;

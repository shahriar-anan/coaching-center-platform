-- Offline instructor payments and "not taken" requests (plan docs/offline-admission/offline-instructor-pay-plan.md, section 4.3).
-- New tables only. Staff entries wait for the Master Admin (PENDING); the Master Admin's own entries are approved at once.

-- A payment to an instructor for one class. A class has at most one live payment: one that is not REJECTED and not VOIDED.
CREATE TABLE offline_class_payments (
    id               UUID PRIMARY KEY,
    class_id         UUID NOT NULL REFERENCES offline_classes (id),
    instructor_id    UUID NOT NULL REFERENCES offline_instructors (id),
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
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_class_payments_amount_check CHECK (amount > 0),
    CONSTRAINT offline_class_payments_method_check CHECK (method IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')),
    CONSTRAINT offline_class_payments_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'VOIDED'))
);

CREATE UNIQUE INDEX offline_class_payments_live_key ON offline_class_payments (class_id)
    WHERE status NOT IN ('REJECTED', 'VOIDED');

-- A "not taken" request for a class and its approval, kept apart from the money.
CREATE TABLE offline_class_decisions (
    id               UUID PRIMARY KEY,
    class_id         UUID NOT NULL REFERENCES offline_classes (id),
    requested_by     UUID NOT NULL REFERENCES users (id),
    requested_at     TIMESTAMPTZ NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    decided_by       UUID NULL REFERENCES users (id),
    decided_at       TIMESTAMPTZ NULL,
    decision_reason  VARCHAR(500) NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_class_decisions_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX offline_class_decisions_class_idx ON offline_class_decisions (class_id);

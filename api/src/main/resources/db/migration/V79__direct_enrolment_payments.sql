-- Direct enrolment payments and installments (plan docs/exam-site/plans/direct-enrolment-installments.md, section 5).
-- New tables only: no existing table or row is changed. Online orders stay in live_exam_orders; a direct enrolment's money
-- lives here, linked to its grant. Receipt numbers reuse live_exam_receipt_seq (V52).

-- One plan per manual grant that was enrolled with a payment. A grant with no payment (No charge) has no plan.
CREATE TABLE live_exam_payment_plans (
    id                UUID PRIMARY KEY,
    grant_id          UUID NOT NULL UNIQUE REFERENCES live_exam_grants (id),
    student_id        UUID NOT NULL REFERENCES users (id),
    list_price        NUMERIC(12,2) NULL,
    discount_percent  INTEGER NULL,
    standard_price    NUMERIC(12,2) NULL,
    agreed_total      NUMERIC(12,2) NOT NULL,
    created_by        UUID NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_payment_plans_total_check CHECK (agreed_total > 0),
    CONSTRAINT live_exam_payment_plans_list_check CHECK (list_price IS NULL OR list_price > 0),
    CONSTRAINT live_exam_payment_plans_standard_check CHECK (standard_price IS NULL OR standard_price > 0),
    CONSTRAINT live_exam_payment_plans_discount_check CHECK (discount_percent IS NULL OR discount_percent BETWEEN 1 AND 99)
);

CREATE INDEX live_exam_payment_plans_student_idx ON live_exam_payment_plans (student_id, created_at DESC);

-- The schedule: row 1 is the payment taken on the day, the rest are the installments still to come.
CREATE TABLE live_exam_installments (
    id       UUID PRIMARY KEY,
    plan_id  UUID NOT NULL REFERENCES live_exam_payment_plans (id),
    seq      SMALLINT NOT NULL,
    amount   NUMERIC(12,2) NOT NULL,
    due_on   DATE NOT NULL,
    CONSTRAINT live_exam_installments_seq_check CHECK (seq BETWEEN 1 AND 24),
    CONSTRAINT live_exam_installments_amount_check CHECK (amount > 0),
    CONSTRAINT live_exam_installments_plan_seq_key UNIQUE (plan_id, seq)
);

-- Every payment taken against a plan, with the figures its receipt shows, fixed when it is recorded.
-- A voided payment stays in the table; its receipt number is never reused.
CREATE TABLE live_exam_direct_payments (
    id                 UUID PRIMARY KEY,
    plan_id            UUID NOT NULL REFERENCES live_exam_payment_plans (id),
    amount             NUMERIC(12,2) NOT NULL,
    method             VARCHAR(16) NOT NULL,
    reference          VARCHAR(100) NOT NULL,
    paid_on            DATE NOT NULL,
    note               VARCHAR(500) NULL,
    receipt_no         VARCHAR(24) NOT NULL UNIQUE,
    paid_to_date       NUMERIC(12,2) NOT NULL,
    due_after          NUMERIC(12,2) NOT NULL,
    next_due_on        DATE NULL,
    next_due_amount    NUMERIC(12,2) NULL,
    installment_no     SMALLINT NOT NULL,
    installment_count  SMALLINT NOT NULL,
    created_by         UUID NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    voided_at          TIMESTAMPTZ NULL,
    voided_by          UUID NULL REFERENCES users (id),
    void_reason        VARCHAR(500) NULL,
    CONSTRAINT live_exam_direct_payments_amount_check CHECK (amount > 0),
    CONSTRAINT live_exam_direct_payments_method_check CHECK (method IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK')),
    CONSTRAINT live_exam_direct_payments_reference_check CHECK (length(btrim(reference)) > 0),
    CONSTRAINT live_exam_direct_payments_due_check CHECK (due_after >= 0),
    CONSTRAINT live_exam_direct_payments_void_check CHECK (
        (voided_at IS NULL) = (voided_by IS NULL) AND (voided_at IS NULL) = (void_reason IS NULL))
);

CREATE INDEX live_exam_direct_payments_plan_idx ON live_exam_direct_payments (plan_id, created_at);

-- The same reference cannot be used twice on one plan while it stands (ignoring case). Voided payments do not count.
CREATE UNIQUE INDEX live_exam_direct_payments_reference_unique
    ON live_exam_direct_payments (plan_id, upper(reference)) WHERE voided_at IS NULL;

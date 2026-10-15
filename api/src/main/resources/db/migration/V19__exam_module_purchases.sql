CREATE TABLE exam_module_purchases (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES users (id),
    module_id       UUID NOT NULL REFERENCES exam_modules (id),
    status          VARCHAR(32) NOT NULL,
    price_paid      NUMERIC(12, 2) NOT NULL,
    started_at      TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT exam_module_purchases_status_check CHECK (status IN ('AWAITING_PAYMENT', 'ACTIVE', 'PAYMENT_FAILED'))
);

ALTER TABLE payments DROP CONSTRAINT payments_payable_type_check;

ALTER TABLE payments
    ADD CONSTRAINT payments_payable_type_check CHECK (payable_type IN ('COURSE_ENROLLMENT', 'BOOK_ORDER', 'EXAM_MODULE'));

ALTER TABLE live_exam_modules ADD COLUMN price NUMERIC(12, 2) NULL;
ALTER TABLE live_exams ADD COLUMN price NUMERIC(12, 2) NULL;

ALTER TABLE live_exam_modules
    ADD CONSTRAINT live_exam_modules_price_check CHECK (price IS NULL OR price > 0);
ALTER TABLE live_exams
    ADD CONSTRAINT live_exams_price_check CHECK (price IS NULL OR price > 0);

CREATE TABLE live_exam_orders (
    id           UUID PRIMARY KEY,
    student_id   UUID NOT NULL REFERENCES users (id),
    scope        VARCHAR(8) NOT NULL,
    module_id    UUID NOT NULL REFERENCES live_exam_modules (id),
    exam_id      UUID NULL REFERENCES live_exams (id),
    amount       NUMERIC(12, 2) NOT NULL,
    currency     CHAR(3) NOT NULL,
    method       VARCHAR(16) NOT NULL,
    tran_id      VARCHAR(30) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    val_id       VARCHAR(128) NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_orders_scope_check CHECK (
        (scope = 'MODULE' AND exam_id IS NULL)
        OR (scope = 'EXAM' AND exam_id IS NOT NULL)
    ),
    CONSTRAINT live_exam_orders_status_check CHECK (
        status IN ('INITIATED', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT live_exam_orders_amount_check CHECK (amount > 0),
    CONSTRAINT live_exam_orders_currency_check CHECK (currency = 'BDT'),
    CONSTRAINT live_exam_orders_method_check CHECK (method = 'SSLCOMMERZ'),
    CONSTRAINT live_exam_orders_tran_id_unique UNIQUE (tran_id)
);

CREATE UNIQUE INDEX live_exam_orders_open_module_unique
    ON live_exam_orders (student_id, module_id) WHERE status = 'INITIATED' AND scope = 'MODULE';
CREATE UNIQUE INDEX live_exam_orders_open_exam_unique
    ON live_exam_orders (student_id, exam_id) WHERE status = 'INITIATED' AND scope = 'EXAM';

CREATE TABLE live_exam_grants (
    id          UUID PRIMARY KEY,
    student_id  UUID NOT NULL REFERENCES users (id),
    scope       VARCHAR(8) NOT NULL,
    module_id   UUID NOT NULL REFERENCES live_exam_modules (id),
    exam_id     UUID NULL REFERENCES live_exams (id),
    order_id    UUID NOT NULL UNIQUE REFERENCES live_exam_orders (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_grants_scope_check CHECK (
        (scope = 'MODULE' AND exam_id IS NULL)
        OR (scope = 'EXAM' AND exam_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX live_exam_grants_module_unique
    ON live_exam_grants (student_id, module_id) WHERE scope = 'MODULE';
CREATE UNIQUE INDEX live_exam_grants_exam_unique
    ON live_exam_grants (student_id, exam_id) WHERE scope = 'EXAM';

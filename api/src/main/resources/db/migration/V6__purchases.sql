CREATE TABLE enrollments (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES users (id),
    course_id       UUID NOT NULL REFERENCES courses (id),
    opening_number  INTEGER NOT NULL,
    delivery_mode   VARCHAR(16) NOT NULL,
    source          VARCHAR(16) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    price_paid      NUMERIC(12, 2) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT enrollments_delivery_mode_check CHECK (delivery_mode IN ('ONLINE', 'OFFLINE', 'HYBRID')),
    CONSTRAINT enrollments_source_check CHECK (source IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT enrollments_status_check CHECK (status IN ('AWAITING_PAYMENT', 'ACTIVE', 'PAYMENT_FAILED'))
);

CREATE TABLE book_orders (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES users (id),
    book_id         UUID NOT NULL REFERENCES books (id),
    fulfillment     VARCHAR(16) NOT NULL,
    payment_method  VARCHAR(16) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    address         TEXT NULL,
    area            VARCHAR(255) NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT book_orders_fulfillment_check CHECK (fulfillment IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT book_orders_payment_method_check CHECK (payment_method IN ('ONLINE', 'CASH')),
    CONSTRAINT book_orders_status_check CHECK (status IN ('PLACED', 'PAID', 'READY', 'DELIVERED', 'COLLECTED', 'PAYMENT_FAILED'))
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    student_id      UUID NOT NULL REFERENCES users (id),
    payable_type    VARCHAR(32) NOT NULL,
    payable_id      UUID NOT NULL,
    amount          NUMERIC(12, 2) NOT NULL,
    method          VARCHAR(16) NOT NULL,
    transaction_ref VARCHAR(128) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT payments_payable_type_check CHECK (payable_type IN ('COURSE_ENROLLMENT', 'BOOK_ORDER')),
    CONSTRAINT payments_method_check CHECK (method IN ('BKASH', 'NAGAD')),
    CONSTRAINT payments_status_check CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    CONSTRAINT payments_transaction_ref_key UNIQUE (transaction_ref)
);

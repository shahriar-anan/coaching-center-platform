-- Offline (in-person) admissions, batch record (plan docs/offline-admission). New tables only: no existing table or row is changed.
-- A batch is a class group a student is admitted into. Its routine file is stored like other uploads (stored name only, no bytes in the row).

CREATE TABLE offline_batches (
    id                       UUID PRIMARY KEY,
    name_en                  VARCHAR(200) NOT NULL,
    name_bn                  VARCHAR(200) NOT NULL,
    starts_on                DATE NULL,
    ends_on                  DATE NOT NULL,
    default_monthly_fee      NUMERIC(12,2) NOT NULL,
    routine_stored_name      VARCHAR(255) NULL,
    routine_original_name    VARCHAR(500) NULL,
    routine_byte_size        BIGINT NULL,
    routine_content_type     VARCHAR(255) NULL,
    routine_uploaded_by      UUID NULL REFERENCES users (id),
    routine_uploaded_at      TIMESTAMPTZ NULL,
    status                   VARCHAR(16) NOT NULL,
    created_by               UUID NOT NULL REFERENCES users (id),
    created_at               TIMESTAMPTZ NOT NULL,
    updated_at               TIMESTAMPTZ NOT NULL,
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_batches_fee_check CHECK (default_monthly_fee >= 0),
    CONSTRAINT offline_batches_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

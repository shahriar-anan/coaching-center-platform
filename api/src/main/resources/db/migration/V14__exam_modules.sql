CREATE TABLE exam_modules (
    id              UUID PRIMARY KEY,
    title_en        VARCHAR(500) NOT NULL,
    title_bn        VARCHAR(500) NOT NULL,
    description_en  TEXT NOT NULL,
    description_bn  TEXT NOT NULL,
    price           NUMERIC(12, 2) NULL,
    status          VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT exam_modules_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

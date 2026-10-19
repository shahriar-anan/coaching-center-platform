CREATE TABLE live_exam_categories (
    id          UUID PRIMARY KEY,
    parent_id   UUID NULL REFERENCES live_exam_categories (id),
    title_en    VARCHAR(500) NOT NULL,
    title_bn    VARCHAR(500) NOT NULL,
    position    INTEGER NOT NULL,
    archived_at TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL
);

CREATE INDEX live_exam_categories_parent_idx ON live_exam_categories (parent_id, position)
    WHERE deleted_at IS NULL;

CREATE TABLE live_exam_modules (
    id              UUID PRIMARY KEY,
    category_id     UUID NOT NULL REFERENCES live_exam_categories (id),
    title_en        VARCHAR(500) NOT NULL,
    title_bn        VARCHAR(500) NOT NULL,
    description_en  TEXT NOT NULL,
    description_bn  TEXT NOT NULL,
    access_mode     VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    finally_saved   BOOLEAN NOT NULL,
    position        INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT live_exam_modules_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT live_exam_modules_access_mode_check CHECK (access_mode = 'MANUAL')
);

CREATE INDEX live_exam_modules_category_idx ON live_exam_modules (category_id, position)
    WHERE deleted_at IS NULL;

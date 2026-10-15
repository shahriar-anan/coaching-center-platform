CREATE TABLE written_submissions (
    id                  UUID PRIMARY KEY,
    component_id        UUID NOT NULL REFERENCES paper_components (id),
    student_id          UUID NOT NULL REFERENCES users (id),
    status              VARCHAR(16) NOT NULL,
    submitted_at        TIMESTAMPTZ NULL,
    pass_percent        INTEGER NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT written_submissions_status_check CHECK (status IN ('DRAFT', 'UNDER_REVIEW', 'EVALUATED'))
);

CREATE UNIQUE INDEX written_submissions_component_student_idx ON written_submissions (component_id, student_id)
    WHERE deleted_at IS NULL;

CREATE TABLE written_submission_images (
    id                  UUID PRIMARY KEY,
    submission_id       UUID NOT NULL REFERENCES written_submissions (id),
    stored_file_name    VARCHAR(255) NOT NULL,
    original_file_name  VARCHAR(255) NOT NULL,
    byte_size           BIGINT NOT NULL,
    content_type        VARCHAR(64) NOT NULL,
    position            INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE TABLE written_submission_questions (
    id                  UUID PRIMARY KEY,
    submission_id       UUID NOT NULL REFERENCES written_submissions (id),
    question_id         UUID NOT NULL,
    stem                TEXT NOT NULL,
    explanation         TEXT NULL,
    max_marks           NUMERIC(12, 2) NOT NULL,
    position            INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

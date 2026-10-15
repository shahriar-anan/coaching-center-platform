CREATE TABLE mcq_attempts (
    id                  UUID PRIMARY KEY,
    component_id        UUID NOT NULL REFERENCES paper_components (id),
    student_id          UUID NOT NULL REFERENCES users (id),
    started_at          TIMESTAMPTZ NOT NULL,
    duration_seconds    INTEGER NOT NULL,
    submitted_at        TIMESTAMPTZ NULL,
    score               NUMERIC(12, 2) NULL,
    max_score           NUMERIC(12, 2) NOT NULL,
    pass_percent        INTEGER NULL,
    status              VARCHAR(16) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT mcq_attempts_status_check CHECK (status IN ('IN_PROGRESS', 'SUBMITTED'))
);

CREATE UNIQUE INDEX mcq_attempts_component_student_idx ON mcq_attempts (component_id, student_id)
    WHERE deleted_at IS NULL;

CREATE TABLE mcq_attempt_questions (
    id                  UUID PRIMARY KEY,
    attempt_id          UUID NOT NULL REFERENCES mcq_attempts (id),
    question_id         UUID NOT NULL,
    position            INTEGER NOT NULL,
    stem                TEXT NOT NULL,
    explanation         TEXT NULL,
    marks               NUMERIC(12, 2) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE TABLE mcq_attempt_options (
    id                  UUID PRIMARY KEY,
    attempt_question_id UUID NOT NULL REFERENCES mcq_attempt_questions (id),
    source_option_id    UUID NOT NULL,
    text                TEXT NOT NULL,
    position            INTEGER NOT NULL,
    correct             BOOLEAN NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE TABLE mcq_answers (
    id                  UUID PRIMARY KEY,
    attempt_id          UUID NOT NULL REFERENCES mcq_attempts (id),
    attempt_question_id UUID NOT NULL REFERENCES mcq_attempt_questions (id),
    attempt_option_id   UUID NULL REFERENCES mcq_attempt_options (id),
    marks_awarded       NUMERIC(12, 2) NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX mcq_answers_attempt_question_idx ON mcq_answers (attempt_id, attempt_question_id)
    WHERE deleted_at IS NULL;

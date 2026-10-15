CREATE TABLE paper_components (
    id              UUID PRIMARY KEY,
    paper_id        UUID NOT NULL REFERENCES exam_nodes (id),
    component_type  VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT paper_components_type_check CHECK (component_type IN ('MCQ', 'WRITTEN'))
);

CREATE UNIQUE INDEX paper_components_paper_type_idx ON paper_components (paper_id, component_type)
    WHERE deleted_at IS NULL;

CREATE TABLE questions (
    id              UUID PRIMARY KEY,
    question_type   VARCHAR(16) NOT NULL,
    stem            TEXT NOT NULL,
    explanation     TEXT NULL,
    marks           NUMERIC(12, 2) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT questions_type_check CHECK (question_type IN ('MCQ', 'WRITTEN'))
);

CREATE TABLE question_options (
    id              UUID PRIMARY KEY,
    question_id     UUID NOT NULL REFERENCES questions (id),
    text            TEXT NOT NULL,
    correct         BOOLEAN NOT NULL,
    position        INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL
);

CREATE TABLE component_questions (
    id              UUID PRIMARY KEY,
    component_id    UUID NOT NULL REFERENCES paper_components (id),
    question_id     UUID NOT NULL REFERENCES questions (id),
    position        INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX component_questions_pair_idx ON component_questions (component_id, question_id)
    WHERE deleted_at IS NULL;

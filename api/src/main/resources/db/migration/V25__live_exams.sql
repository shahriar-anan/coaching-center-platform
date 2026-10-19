CREATE TABLE live_exams (
    id                          UUID PRIMARY KEY,
    module_id                   UUID NOT NULL REFERENCES live_exam_modules (id),
    exam_type                   VARCHAR(16) NOT NULL,
    title_en                    VARCHAR(500) NOT NULL,
    title_bn                    VARCHAR(500) NOT NULL,
    description_en              TEXT NOT NULL,
    description_bn              TEXT NOT NULL,
    rules_en                    TEXT NOT NULL,
    rules_bn                    TEXT NOT NULL,
    live_at                     TIMESTAMPTZ NOT NULL,
    duration_minutes            INTEGER NOT NULL,
    pass_percent                INTEGER NULL,
    written_maximum             INTEGER NULL,
    question_stored_file_name   VARCHAR(255) NULL,
    question_original_file_name VARCHAR(500) NULL,
    question_byte_size          BIGINT NULL,
    question_content_type       VARCHAR(255) NULL,
    status                      VARCHAR(16) NOT NULL,
    finally_saved               BOOLEAN NOT NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    deleted_at                  TIMESTAMPTZ NULL,
    CONSTRAINT live_exams_type_check CHECK (exam_type IN ('MCQ', 'WRITTEN')),
    CONSTRAINT live_exams_status_check CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT live_exams_duration_check CHECK (duration_minutes > 0),
    CONSTRAINT live_exams_pass_percent_check CHECK (pass_percent IS NULL OR pass_percent BETWEEN 0 AND 100),
    CONSTRAINT live_exams_written_maximum_check CHECK (
        (exam_type = 'MCQ' AND written_maximum IS NULL)
        OR (exam_type = 'WRITTEN' AND (written_maximum IS NULL OR written_maximum > 0))
    )
);

CREATE INDEX live_exams_module_idx ON live_exams (module_id)
    WHERE deleted_at IS NULL;

CREATE TABLE live_exam_questions (
    id          UUID PRIMARY KEY,
    exam_id     UUID NOT NULL REFERENCES live_exams (id),
    stem        TEXT NOT NULL,
    marks       INTEGER NOT NULL,
    explanation TEXT NULL,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL,
    CONSTRAINT live_exam_questions_marks_check CHECK (marks > 0)
);

CREATE INDEX live_exam_questions_exam_idx ON live_exam_questions (exam_id, position)
    WHERE deleted_at IS NULL;

CREATE TABLE live_exam_options (
    id          UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES live_exam_questions (id),
    option_text TEXT NOT NULL,
    correct     BOOLEAN NOT NULL,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX live_exam_options_one_correct_idx ON live_exam_options (question_id)
    WHERE correct AND deleted_at IS NULL;

CREATE INDEX live_exam_options_question_idx ON live_exam_options (question_id, position)
    WHERE deleted_at IS NULL;

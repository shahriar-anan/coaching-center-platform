CREATE TABLE live_exam_participants (
    id                          UUID PRIMARY KEY,
    exam_id                     UUID NOT NULL REFERENCES live_exams (id),
    student_id                  UUID NOT NULL REFERENCES users (id),
    kind                        VARCHAR(16) NOT NULL,
    started_at                  TIMESTAMPTZ NOT NULL,
    deadline_at                 TIMESTAMPTZ NOT NULL,
    submitted_at                TIMESTAMPTZ NULL,
    score                       INTEGER NULL,
    passed                      BOOLEAN NULL,
    status                      VARCHAR(32) NOT NULL,
    answer_stored_file_name     VARCHAR(255) NULL,
    answer_original_file_name   VARCHAR(500) NULL,
    answer_byte_size            BIGINT NULL,
    answer_content_type         VARCHAR(255) NULL,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    deleted_at                  TIMESTAMPTZ NULL,
    CONSTRAINT live_exam_participants_kind_check CHECK (kind IN ('LIVE', 'RETAKE')),
    CONSTRAINT live_exam_participants_status_check CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'FAILED_TO_SUBMIT'))
);

CREATE UNIQUE INDEX live_exam_participants_one_idx ON live_exam_participants (exam_id, student_id)
    WHERE deleted_at IS NULL;

CREATE TABLE live_exam_answers (
    participant_id UUID NOT NULL REFERENCES live_exam_participants (id),
    question_id    UUID NOT NULL REFERENCES live_exam_questions (id),
    option_id      UUID NOT NULL REFERENCES live_exam_options (id),
    PRIMARY KEY (participant_id, question_id)
);

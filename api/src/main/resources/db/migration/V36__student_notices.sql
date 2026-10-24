CREATE TABLE student_notices (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id),
    kind            VARCHAR(32) NOT NULL,
    message_key     VARCHAR(220) NOT NULL,
    exam_id         UUID NOT NULL REFERENCES live_exams (id),
    participant_id  UUID NULL REFERENCES live_exam_participants (id),
    created_at      TIMESTAMPTZ NOT NULL,
    read_at         TIMESTAMPTZ NULL,
    CONSTRAINT student_notices_kind_check CHECK (
        kind IN ('EXAM_REMINDER', 'MCQ_SCORE', 'WRITTEN_RESULT')
    ),
    CONSTRAINT student_notices_key_unique UNIQUE (kind, message_key)
);

CREATE INDEX student_notices_user_idx ON student_notices (user_id, created_at DESC);
CREATE INDEX student_notices_unread_idx ON student_notices (user_id) WHERE read_at IS NULL;

CREATE TABLE course_routine_slots (
    id          UUID PRIMARY KEY,
    course_id   UUID NOT NULL REFERENCES courses (id),
    weekday     SMALLINT NOT NULL,
    start_time  TIME NOT NULL,
    end_time    TIME NOT NULL,
    place       TEXT NULL,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL,
    CONSTRAINT course_routine_weekday_check CHECK (weekday BETWEEN 1 AND 7)
);

CREATE INDEX course_routine_course_idx ON course_routine_slots (course_id, position)
    WHERE deleted_at IS NULL;

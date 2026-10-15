CREATE TABLE course_instructors (
    id             UUID PRIMARY KEY,
    course_id      UUID NOT NULL REFERENCES courses (id),
    name           VARCHAR(500) NOT NULL,
    description    TEXT NULL,
    photo_file_id  UUID NULL REFERENCES materials (id),
    position       INTEGER NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    deleted_at     TIMESTAMPTZ NULL
);

CREATE INDEX course_instructors_course_idx ON course_instructors (course_id, position)
    WHERE deleted_at IS NULL;

-- A student's optional profile picture (notice media plan, F2). One row per student; no row means no picture.
CREATE TABLE student_photos (
    user_id       UUID PRIMARY KEY REFERENCES users (id),
    stored_name   VARCHAR(64) NOT NULL,
    content_type  VARCHAR(64) NOT NULL,
    byte_size     BIGINT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

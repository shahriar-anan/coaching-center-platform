ALTER TABLE users DROP CONSTRAINT users_role_check;

ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('STUDENT', 'MASTER_ADMIN', 'SYSTEM_ADMIN', 'INSTRUCTOR'));

CREATE TABLE live_exam_instructors (
    module_id      UUID PRIMARY KEY REFERENCES live_exam_modules (id),
    instructor_id  UUID NOT NULL REFERENCES users (id),
    assigned_at    TIMESTAMPTZ NOT NULL
);

ALTER TABLE live_exam_participants
    ADD COLUMN comment                      TEXT NULL,
    ADD COLUMN evaluated_by                 UUID NULL REFERENCES users (id),
    ADD COLUMN evaluated_at                 TIMESTAMPTZ NULL,
    ADD COLUMN evaluated_stored_file_name   VARCHAR(255) NULL,
    ADD COLUMN evaluated_original_file_name VARCHAR(500) NULL,
    ADD COLUMN evaluated_byte_size          BIGINT NULL,
    ADD COLUMN evaluated_content_type       VARCHAR(255) NULL;

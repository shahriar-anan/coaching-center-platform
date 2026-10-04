ALTER TABLE content_nodes
    ADD COLUMN video_provider VARCHAR(32) NULL,
    ADD COLUMN video_ref VARCHAR(64) NULL,
    ADD COLUMN notes TEXT NULL;

CREATE TABLE materials (
    id                  UUID PRIMARY KEY,
    course_id           UUID NOT NULL REFERENCES courses (id),
    lecture_node_id     UUID NULL REFERENCES content_nodes (id),
    stored_file_name    VARCHAR(255) NOT NULL,
    original_file_name  VARCHAR(500) NOT NULL,
    byte_size           BIGINT NOT NULL,
    content_type        VARCHAR(255) NOT NULL,
    free                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

ALTER TABLE courses
    ADD COLUMN cover_file_id UUID NULL REFERENCES materials (id);

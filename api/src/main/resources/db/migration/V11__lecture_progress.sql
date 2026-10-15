CREATE TABLE lecture_progress (
    id               UUID PRIMARY KEY,
    student_id       UUID NOT NULL REFERENCES users (id),
    course_id        UUID NOT NULL REFERENCES courses (id),
    lecture_node_id  UUID NOT NULL REFERENCES content_nodes (id),
    watched          BOOLEAN NOT NULL DEFAULT FALSE,
    last_opened_at   TIMESTAMPTZ NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    deleted_at       TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX lecture_progress_student_lecture_idx
    ON lecture_progress (student_id, lecture_node_id)
    WHERE deleted_at IS NULL;

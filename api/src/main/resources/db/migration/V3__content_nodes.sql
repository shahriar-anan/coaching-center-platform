CREATE TABLE content_nodes (
    id              UUID PRIMARY KEY,
    course_id       UUID NOT NULL REFERENCES courses (id),
    parent_id       UUID NULL REFERENCES content_nodes (id),
    node_type       VARCHAR(16) NOT NULL,
    title           VARCHAR(500) NOT NULL,
    position        INTEGER NOT NULL,
    free            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT content_nodes_node_type_check CHECK (node_type IN ('SECTION', 'LECTURE'))
);

CREATE INDEX content_nodes_course_parent_idx ON content_nodes (course_id, parent_id, position)
    WHERE deleted_at IS NULL;

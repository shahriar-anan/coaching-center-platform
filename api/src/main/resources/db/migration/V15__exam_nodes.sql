CREATE TABLE exam_nodes (
    id              UUID PRIMARY KEY,
    module_id       UUID NOT NULL REFERENCES exam_modules (id),
    parent_id       UUID NULL REFERENCES exam_nodes (id),
    node_type       VARCHAR(16) NOT NULL,
    title_en        VARCHAR(500) NOT NULL,
    title_bn        VARCHAR(500) NOT NULL,
    position        INTEGER NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT exam_nodes_node_type_check CHECK (node_type IN ('SECTION', 'PAPER'))
);

CREATE INDEX exam_nodes_module_parent_idx ON exam_nodes (module_id, parent_id, position)
    WHERE deleted_at IS NULL;

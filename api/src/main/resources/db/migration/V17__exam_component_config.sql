ALTER TABLE exam_nodes
    ADD COLUMN pass_percent INTEGER NULL;

ALTER TABLE paper_components
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN description_bn TEXT NULL,
    ADD COLUMN starts_at TIMESTAMPTZ NULL,
    ADD COLUMN ends_at TIMESTAMPTZ NULL,
    ADD COLUMN duration_seconds INTEGER NULL,
    ADD COLUMN immediate_results BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN scheduled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deadline_at TIMESTAMPTZ NULL;

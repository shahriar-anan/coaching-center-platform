ALTER TABLE written_submission_questions
    ADD COLUMN marks_awarded NUMERIC(12, 2) NULL;

ALTER TABLE written_submissions
    ADD COLUMN feedback TEXT NULL,
    ADD COLUMN evaluated_at TIMESTAMPTZ NULL,
    ADD COLUMN evaluated_by UUID NULL REFERENCES users (id);

ALTER TABLE paper_components
    ADD COLUMN results_published BOOLEAN NOT NULL DEFAULT FALSE;

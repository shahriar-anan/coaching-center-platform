-- Public batch catalogue (docs/offline-admission/public-admission-plan.md, sections 3 and 4). Additive only: no existing row or column changes.
-- A batch gets a description in each language, a photo (stored like the profile photo), an admissions switch, a display order, and the
-- default exams and packages an onboarded student is given (P5). The defaults are copied onto each admission at onboarding.

ALTER TABLE offline_batches
    ADD COLUMN description_en      VARCHAR(2000) NULL,
    ADD COLUMN description_bn      VARCHAR(2000) NULL,
    ADD COLUMN photo_stored_name   VARCHAR(255) NULL,
    ADD COLUMN photo_content_type  VARCHAR(100) NULL,
    ADD COLUMN photo_byte_size     BIGINT NULL,
    ADD COLUMN photo_uploaded_by   UUID NULL REFERENCES users (id),
    ADD COLUMN photo_uploaded_at   TIMESTAMPTZ NULL,
    ADD COLUMN admissions_open     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN display_order       INTEGER NOT NULL DEFAULT 0;

-- The default exams and packages of a batch. Replaced as a whole by the office; never history.
CREATE TABLE offline_batch_items (
    id          UUID PRIMARY KEY,
    batch_id    UUID NOT NULL REFERENCES offline_batches (id),
    scope       VARCHAR(8) NOT NULL,
    exam_id     UUID NULL REFERENCES live_exams (id),
    module_id   UUID NULL REFERENCES live_exam_modules (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT offline_batch_items_scope_check CHECK (scope IN ('EXAM', 'MODULE')),
    CONSTRAINT offline_batch_items_target_check CHECK (
        (scope = 'EXAM' AND exam_id IS NOT NULL AND module_id IS NULL)
        OR (scope = 'MODULE' AND module_id IS NOT NULL AND exam_id IS NULL))
);

CREATE UNIQUE INDEX offline_batch_items_exam_unique ON offline_batch_items (batch_id, exam_id) WHERE exam_id IS NOT NULL;
CREATE UNIQUE INDEX offline_batch_items_module_unique ON offline_batch_items (batch_id, module_id) WHERE module_id IS NOT NULL;

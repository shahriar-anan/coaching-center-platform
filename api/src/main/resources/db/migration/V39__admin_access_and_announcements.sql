-- Phase 2 admin access, plan section 7. Only API-ADMIN writes V39.

-- Grants: manual and bundle grants have no order, and a revoke keeps the row.
ALTER TABLE live_exam_grants ALTER COLUMN order_id DROP NOT NULL;
ALTER TABLE live_exam_grants DROP CONSTRAINT live_exam_grants_order_id_key;
ALTER TABLE live_exam_grants
    ADD COLUMN source VARCHAR(8) NOT NULL DEFAULT 'ORDER',
    ADD COLUMN granted_by UUID NULL REFERENCES users (id),
    ADD COLUMN reason VARCHAR(500) NULL,
    ADD COLUMN revoked_at TIMESTAMPTZ NULL,
    ADD COLUMN revoked_by UUID NULL REFERENCES users (id),
    ADD COLUMN revoke_reason VARCHAR(500) NULL,
    ADD CONSTRAINT live_exam_grants_source_check CHECK (source IN ('ORDER', 'MANUAL', 'BUNDLE'));
ALTER TABLE live_exam_grants ALTER COLUMN source DROP DEFAULT;

-- One active grant per student and target. A revoked grant no longer blocks a new one.
DROP INDEX live_exam_grants_module_unique;
DROP INDEX live_exam_grants_exam_unique;
CREATE UNIQUE INDEX live_exam_grants_module_unique
    ON live_exam_grants (student_id, module_id) WHERE scope = 'MODULE' AND revoked_at IS NULL;
CREATE UNIQUE INDEX live_exam_grants_exam_unique
    ON live_exam_grants (student_id, exam_id) WHERE scope = 'EXAM' AND revoked_at IS NULL;

-- Orders: a bundle is one order with several exams. Confirm and reject record who and why.
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_scope_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_scope_check CHECK (
    (scope = 'MODULE' AND exam_id IS NULL)
    OR (scope = 'EXAM' AND exam_id IS NOT NULL)
    OR (scope = 'BUNDLE' AND exam_id IS NULL)
);
ALTER TABLE live_exam_orders
    ADD COLUMN confirmed_by UUID NULL REFERENCES users (id),
    ADD COLUMN confirm_reason VARCHAR(500) NULL,
    ADD COLUMN rejected_reason VARCHAR(500) NULL;

CREATE TABLE live_exam_order_exams (
    order_id    UUID NOT NULL REFERENCES live_exam_orders (id),
    exam_id     UUID NOT NULL REFERENCES live_exams (id),
    PRIMARY KEY (order_id, exam_id)
);

-- MCQ answer release, per exam.
ALTER TABLE live_exams
    ADD COLUMN answers_released_at TIMESTAMPTZ NULL,
    ADD COLUMN answers_released_by UUID NULL REFERENCES users (id);

-- Staff announcements.
CREATE TABLE announcements (
    id          UUID PRIMARY KEY,
    title_en    VARCHAR(500) NULL,
    title_bn    VARCHAR(500) NULL,
    body_en     TEXT NULL,
    body_bn     TEXT NULL,
    audience    VARCHAR(8) NOT NULL,
    module_id   UUID NULL REFERENCES live_exam_modules (id),
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT announcements_audience_check CHECK (audience IN ('ALL', 'MODULE'))
);

ALTER TABLE student_notices ADD COLUMN announcement_id UUID NULL REFERENCES announcements (id);

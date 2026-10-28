-- NR-01: percent discounts with an optional end, free exams, access days per exam, popular flags, preview, and favourites.

ALTER TABLE live_exams
    ADD COLUMN is_free BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN discount_percent INTEGER NULL,
    ADD COLUMN discount_ends_at TIMESTAMPTZ NULL,
    ADD COLUMN access_days INTEGER NULL,
    ADD COLUMN popular BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN preview_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE live_exams ADD CONSTRAINT live_exams_discount_percent_check CHECK (
    discount_percent BETWEEN 1 AND 99
);
ALTER TABLE live_exams ADD CONSTRAINT live_exams_access_days_check CHECK (access_days > 0);
ALTER TABLE live_exams ADD CONSTRAINT live_exams_free_check CHECK (
    NOT is_free OR (price IS NULL AND discount_percent IS NULL)
);

ALTER TABLE live_exam_modules
    ADD COLUMN discount_percent INTEGER NULL,
    ADD COLUMN discount_ends_at TIMESTAMPTZ NULL,
    ADD COLUMN popular BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE live_exam_modules ADD CONSTRAINT live_exam_modules_discount_percent_check CHECK (
    discount_percent BETWEEN 1 AND 99
);

-- A MODULE order names its package. An EXAM order names its exam only. A BUNDLE names its package and no exam.
ALTER TABLE live_exam_orders ALTER COLUMN module_id DROP NOT NULL;
ALTER TABLE live_exam_orders ADD COLUMN list_price NUMERIC(12, 2) NULL;
ALTER TABLE live_exam_orders ADD COLUMN discount_percent INTEGER NULL;
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_scope_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_scope_check CHECK (
    (scope = 'MODULE' AND module_id IS NOT NULL AND exam_id IS NULL)
    OR (scope = 'EXAM' AND exam_id IS NOT NULL)
    OR (scope = 'BUNDLE' AND module_id IS NOT NULL AND exam_id IS NULL)
);

-- An exam grant names its exam; the package is optional on it.
ALTER TABLE live_exam_grants ALTER COLUMN module_id DROP NOT NULL;

CREATE TABLE student_favourites (
    id          UUID PRIMARY KEY,
    student_id  UUID NOT NULL REFERENCES users (id),
    exam_id     UUID NULL REFERENCES live_exams (id),
    module_id   UUID NULL REFERENCES live_exam_modules (id),
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT student_favourites_one_target_check CHECK (num_nonnulls(exam_id, module_id) = 1)
);

CREATE UNIQUE INDEX student_favourites_exam_unique ON student_favourites (student_id, exam_id)
    WHERE exam_id IS NOT NULL;
CREATE UNIQUE INDEX student_favourites_module_unique ON student_favourites (student_id, module_id)
    WHERE module_id IS NOT NULL;

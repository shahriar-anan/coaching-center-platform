-- NR-01: an exam's home is a category. A package (live_exam_modules) bundles zero or more exams, and an exam may sit in
-- several packages. live_exams.module_id stays as "created in" for history; no rule reads it after NR-01.
-- The backfills use gen_random_uuid(), which needs PostgreSQL 13 or later.

ALTER TABLE live_exams ADD COLUMN category_id UUID NULL REFERENCES live_exam_categories (id);

UPDATE live_exams e
SET category_id = m.category_id
FROM live_exam_modules m
WHERE m.id = e.module_id;

ALTER TABLE live_exams ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE live_exams ALTER COLUMN module_id DROP NOT NULL;

CREATE INDEX live_exams_category_idx ON live_exams (category_id) WHERE deleted_at IS NULL;

CREATE TABLE live_exam_package_exams (
    id          UUID PRIMARY KEY,
    module_id   UUID NOT NULL REFERENCES live_exam_modules (id),
    exam_id     UUID NOT NULL REFERENCES live_exams (id),
    position    INTEGER NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL,
    removed_at  TIMESTAMPTZ NULL
);

-- A removed row stays as history, so the same exam can be added to the same package again.
CREATE UNIQUE INDEX live_exam_package_exams_active_idx ON live_exam_package_exams (module_id, exam_id)
    WHERE removed_at IS NULL;
CREATE INDEX live_exam_package_exams_exam_idx ON live_exam_package_exams (exam_id);
CREATE INDEX live_exam_package_exams_module_idx ON live_exam_package_exams (module_id, position);

INSERT INTO live_exam_package_exams (id, module_id, exam_id, position, added_at)
SELECT gen_random_uuid(), e.module_id, e.id,
       row_number() OVER (PARTITION BY e.module_id ORDER BY e.created_at, e.id) - 1,
       e.created_at
FROM live_exams e
WHERE e.deleted_at IS NULL AND e.module_id IS NOT NULL;

-- live_exam_instructors stays as the package's default instructor. The exam carries its own from now on.
ALTER TABLE live_exams ADD COLUMN instructor_id UUID NULL REFERENCES users (id);

UPDATE live_exams e
SET instructor_id = i.instructor_id
FROM live_exam_instructors i
WHERE i.module_id = e.module_id;

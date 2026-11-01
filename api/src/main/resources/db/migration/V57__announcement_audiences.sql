-- Announcements to one package, one exam, or selected students, and withdrawal (plan section 7). Notices stay one row per
-- recipient. A withdrawn announcement stays in the table; the inbox and the package and exam pages skip it.
ALTER TABLE announcements
    ADD COLUMN exam_id UUID NULL REFERENCES live_exams (id),
    ADD COLUMN withdrawn_at TIMESTAMPTZ NULL,
    ADD COLUMN withdrawn_by UUID NULL REFERENCES users (id);

ALTER TABLE announcements DROP CONSTRAINT announcements_audience_check;
ALTER TABLE announcements ADD CONSTRAINT announcements_audience_check CHECK (
    audience IN ('ALL', 'MODULE', 'EXAM', 'SELECTED')
);

-- The package is named for a MODULE audience only, the exam for an EXAM audience only, and each is required for its own.
ALTER TABLE announcements ADD CONSTRAINT announcements_target_check CHECK (
    (audience = 'MODULE' AND module_id IS NOT NULL AND exam_id IS NULL)
    OR (audience = 'EXAM' AND exam_id IS NOT NULL AND module_id IS NULL)
    OR (audience IN ('ALL', 'SELECTED') AND module_id IS NULL AND exam_id IS NULL)
);

CREATE INDEX announcements_exam_idx ON announcements (exam_id) WHERE exam_id IS NOT NULL;
CREATE INDEX announcements_module_idx ON announcements (module_id) WHERE module_id IS NOT NULL;

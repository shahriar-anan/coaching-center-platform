ALTER TABLE exam_modules
    ADD COLUMN access_mode VARCHAR(32) NULL,
    ADD COLUMN course_id UUID NULL REFERENCES courses (id);

CREATE INDEX exam_modules_course_id_idx ON exam_modules (course_id)
    WHERE deleted_at IS NULL AND course_id IS NOT NULL;

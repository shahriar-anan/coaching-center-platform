CREATE TABLE live_exam_roster (
    module_id   UUID NOT NULL REFERENCES live_exam_modules (id),
    student_id  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (module_id, student_id)
);

CREATE INDEX live_exam_roster_student_idx ON live_exam_roster (student_id);

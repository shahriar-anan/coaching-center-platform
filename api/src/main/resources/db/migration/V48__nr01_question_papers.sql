-- NR-01: every written question paper is a version. A student and their evaluator see the version that student sat (E29).
-- The live_exams.question_* columns stay and mirror the published paper until a later migration drops them.

CREATE TABLE live_exam_question_papers (
    id                  UUID PRIMARY KEY,
    exam_id             UUID NOT NULL REFERENCES live_exams (id),
    version             INTEGER NOT NULL,
    stored_file_name    VARCHAR(255) NOT NULL,
    original_file_name  VARCHAR(500) NULL,
    byte_size           BIGINT NULL,
    content_type        VARCHAR(255) NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    created_by          UUID NULL REFERENCES users (id),
    CONSTRAINT live_exam_question_papers_version_unique UNIQUE (exam_id, version),
    CONSTRAINT live_exam_question_papers_version_check CHECK (version > 0)
);

-- Version 1 is the paper every exam already has.
INSERT INTO live_exam_question_papers (id, exam_id, version, stored_file_name, original_file_name, byte_size,
                                       content_type, created_at, created_by)
SELECT gen_random_uuid(), e.id, 1, e.question_stored_file_name, e.question_original_file_name, e.question_byte_size,
       e.question_content_type, e.updated_at, NULL
FROM live_exams e
WHERE e.question_stored_file_name IS NOT NULL;

ALTER TABLE live_exams ADD COLUMN question_paper_id UUID NULL REFERENCES live_exam_question_papers (id);

UPDATE live_exams e
SET question_paper_id = p.id
FROM live_exam_question_papers p
WHERE p.exam_id = e.id AND p.version = 1;

ALTER TABLE live_exam_participants ADD COLUMN question_paper_id UUID NULL REFERENCES live_exam_question_papers (id);

UPDATE live_exam_participants p
SET question_paper_id = q.id
FROM live_exams e
JOIN live_exam_question_papers q ON q.exam_id = e.id AND q.version = 1
WHERE p.exam_id = e.id AND e.exam_type = 'WRITTEN';

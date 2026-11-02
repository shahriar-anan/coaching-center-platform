-- A student asks the Master Admin for a written retake. The grant itself is still made by the grant service (V46).
CREATE TABLE live_exam_retake_requests (
    id              UUID PRIMARY KEY,
    exam_id         UUID NOT NULL REFERENCES live_exams (id),
    student_id      UUID NOT NULL REFERENCES users (id),
    status          VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED')),
    reason          VARCHAR(500) NULL,
    decision_note   VARCHAR(500) NULL,
    requested_at    TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ NULL,
    decided_by      UUID NULL REFERENCES users (id),
    grant_id        UUID NULL REFERENCES live_exam_retake_grants (id)
);

-- At most one request waiting for a decision per student and exam.
CREATE UNIQUE INDEX live_exam_retake_requests_pending_idx ON live_exam_retake_requests (exam_id, student_id)
    WHERE status = 'PENDING';

-- A declined retake tells the student, with the note. Every earlier kind stays valid.
ALTER TABLE student_notices DROP CONSTRAINT student_notices_kind_check;
ALTER TABLE student_notices ADD CONSTRAINT student_notices_kind_check CHECK (
    kind IN (
        'EXAM_REMINDER', 'EXAM_STARTING_SOON', 'EXAM_OPEN', 'PURCHASE_GRANTED', 'EXAM_PUBLISHED',
        'RETAKE_AVAILABLE', 'ANNOUNCEMENT', 'MCQ_SCORE', 'WRITTEN_RESULT', 'EXAM_UPDATED',
        'EXAM_TIME_CHANGED', 'ACCESS_GRANTED', 'RETAKE_DECLINED'
    )
);

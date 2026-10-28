-- NR-01: the live window (live_at to ends_at) is separate from the duration. A practice exam has no window.
-- Attempts: a student may have several attempts, but only one open at a time. Kind is set when the attempt starts.

ALTER TABLE live_exams ADD COLUMN ends_at TIMESTAMPTZ NULL;

UPDATE live_exams
SET ends_at = live_at + make_interval(mins => duration_minutes)
WHERE live_at IS NOT NULL;

ALTER TABLE live_exams ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'LIVE';
ALTER TABLE live_exams ALTER COLUMN mode DROP DEFAULT;
ALTER TABLE live_exams ADD CONSTRAINT live_exams_mode_check CHECK (mode IN ('LIVE', 'PRACTICE'));
ALTER TABLE live_exams ADD CONSTRAINT live_exams_window_pair_check CHECK ((live_at IS NULL) = (ends_at IS NULL));
ALTER TABLE live_exams ADD CONSTRAINT live_exams_window_length_check CHECK (
    ends_at IS NULL OR ends_at >= live_at + make_interval(mins => duration_minutes)
);
ALTER TABLE live_exams ADD CONSTRAINT live_exams_practice_window_check CHECK (mode = 'LIVE' OR live_at IS NULL);

-- Set on the first publish and never cleared. A practice exam's access is counted from it.
ALTER TABLE live_exams ADD COLUMN published_at TIMESTAMPTZ NULL;

UPDATE live_exams
SET published_at = updated_at
WHERE status IN ('PUBLISHED', 'ARCHIVED');

DROP INDEX live_exam_participants_one_idx;
ALTER TABLE live_exam_participants DROP CONSTRAINT live_exam_participants_kind_check;

-- Until now a RETAKE was the single later attempt, taken after the window. That attempt was really a first attempt
-- started after the window, so it becomes ARCHIVED.
UPDATE live_exam_participants SET kind = 'ARCHIVED' WHERE kind = 'RETAKE';

ALTER TABLE live_exam_participants ADD CONSTRAINT live_exam_participants_kind_check CHECK (
    kind IN ('LIVE', 'ARCHIVED', 'RETAKE', 'PRACTICE')
);

ALTER TABLE live_exam_participants ADD COLUMN attempt_no INTEGER NOT NULL DEFAULT 1;

CREATE UNIQUE INDEX live_exam_participants_attempt_idx ON live_exam_participants (exam_id, student_id, attempt_no)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX live_exam_participants_open_idx ON live_exam_participants (exam_id, student_id)
    WHERE status = 'IN_PROGRESS' AND deleted_at IS NULL;

-- A written retake: granted by a Master Admin or System Admin, used once. One active grant per student and exam.
CREATE TABLE live_exam_retake_grants (
    id                    UUID PRIMARY KEY,
    exam_id               UUID NOT NULL REFERENCES live_exams (id),
    student_id            UUID NOT NULL REFERENCES users (id),
    granted_by            UUID NOT NULL REFERENCES users (id),
    reason                VARCHAR(500) NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    used_participant_id   UUID NULL REFERENCES live_exam_participants (id),
    revoked_at            TIMESTAMPTZ NULL,
    revoked_by            UUID NULL REFERENCES users (id)
);

CREATE UNIQUE INDEX live_exam_retake_grants_active_idx ON live_exam_retake_grants (exam_id, student_id)
    WHERE revoked_at IS NULL;

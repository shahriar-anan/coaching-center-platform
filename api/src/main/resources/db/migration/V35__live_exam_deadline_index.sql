CREATE INDEX live_exam_participants_due_idx ON live_exam_participants (deadline_at)
    WHERE submitted_at IS NULL AND deleted_at IS NULL;

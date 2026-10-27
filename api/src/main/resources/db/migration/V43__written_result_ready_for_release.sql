-- The marker flags a paper finished: it is ready for the Master Admin to release. Any save clears the flag,
-- and so does a release. The flag is a signal, not a gate.
ALTER TABLE live_exam_participants ADD COLUMN ready_for_release_at timestamptz;

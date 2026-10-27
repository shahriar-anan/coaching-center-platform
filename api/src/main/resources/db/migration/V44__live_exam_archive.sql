-- An archived exam stays off the student website and keeps its posted results.
-- Master Admin only (EXAM_DELETE). Staff still read it, with the Archived status.
ALTER TABLE live_exams DROP CONSTRAINT live_exams_status_check;
ALTER TABLE live_exams ADD CONSTRAINT live_exams_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'));

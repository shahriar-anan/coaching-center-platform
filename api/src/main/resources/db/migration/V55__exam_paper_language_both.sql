-- NR-01 UI Step 1 (D5, C2): paper language can be "Both", and it can be left unset (null) when none was chosen.
-- VARCHAR(2) does not fit "BOTH"; the old check (V30) is dropped by name and replaced. A null value passes the check.
ALTER TABLE live_exams DROP CONSTRAINT live_exams_paper_language_check;
ALTER TABLE live_exams ALTER COLUMN paper_language DROP NOT NULL;
ALTER TABLE live_exams ALTER COLUMN paper_language TYPE VARCHAR(8);
ALTER TABLE live_exams
    ADD CONSTRAINT live_exams_paper_language_check CHECK (paper_language IS NULL OR paper_language IN ('BN', 'EN', 'BOTH'));

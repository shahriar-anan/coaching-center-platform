ALTER TABLE live_exams
    ADD COLUMN paper_language VARCHAR(2) NOT NULL DEFAULT 'BN';

ALTER TABLE live_exams
    ADD CONSTRAINT live_exams_paper_language_check CHECK (paper_language IN ('BN', 'EN'));

ALTER TABLE live_exams
    ALTER COLUMN paper_language DROP DEFAULT;

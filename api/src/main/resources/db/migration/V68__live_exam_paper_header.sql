-- Live exams: the header of an MCQ paper, read from the client's Word file (plan: docs/exam-site/plans/mcq-docx-upload).
-- One line per text line. Shown at the top of the sitting paper in place of the title.

ALTER TABLE live_exams ADD COLUMN paper_header TEXT NULL;

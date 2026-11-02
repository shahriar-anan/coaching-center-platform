-- Live exams: an MCQ attempt keeps the question set it started with (D9). Null for attempts started before this change.

ALTER TABLE live_exam_participants ADD COLUMN question_snapshot TEXT NULL;

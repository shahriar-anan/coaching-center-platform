-- New exams start with answers locked. Exams that already exist keep their setting.
ALTER TABLE live_exams ALTER COLUMN answers_changeable SET DEFAULT false;

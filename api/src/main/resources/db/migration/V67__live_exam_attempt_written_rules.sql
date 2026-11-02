-- Live exams: a written attempt keeps the maximum and the pass percent it was sat on, so a later change to the exam does not
-- change how a paper is marked, judged or shown. Attempts from before this change take the exam's current values.

ALTER TABLE live_exam_participants ADD COLUMN written_maximum INTEGER NULL;
ALTER TABLE live_exam_participants ADD COLUMN written_pass_percent INTEGER NULL;

UPDATE live_exam_participants p
   SET written_maximum = e.written_maximum,
       written_pass_percent = e.pass_percent
  FROM live_exams e
 WHERE p.exam_id = e.id
   AND e.exam_type = 'WRITTEN';

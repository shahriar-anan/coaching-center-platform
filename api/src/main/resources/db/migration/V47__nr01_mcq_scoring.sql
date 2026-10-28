-- NR-01: multi-answer MCQ, negative marks per exam, and the "answers can be changed" switch.

-- A question may have several correct options. The one-correct rule goes.
DROP INDEX live_exam_options_one_correct_idx;

ALTER TABLE live_exam_questions ADD COLUMN multiple_answer BOOLEAN NOT NULL DEFAULT false;

-- One row per chosen option, so a multi-answer question stores every choice.
ALTER TABLE live_exam_answers DROP CONSTRAINT live_exam_answers_pkey;
ALTER TABLE live_exam_answers ADD PRIMARY KEY (participant_id, question_id, option_id);

-- Null or 0 means no negative marking. Existing exams had none.
ALTER TABLE live_exams ADD COLUMN negative_marks NUMERIC(5, 2) NULL;
ALTER TABLE live_exams ADD CONSTRAINT live_exams_negative_marks_check CHECK (
    negative_marks IS NULL OR negative_marks >= 0
);

ALTER TABLE live_exams ADD COLUMN answers_changeable BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE live_exam_participants ALTER COLUMN score TYPE NUMERIC(8, 2);
ALTER TABLE live_exam_participants ALTER COLUMN pending_score TYPE NUMERIC(8, 2);

-- With "answers can be changed" off, a question is final once confirmed (R9).
CREATE TABLE live_exam_answer_confirmations (
    participant_id  UUID NOT NULL REFERENCES live_exam_participants (id),
    question_id     UUID NOT NULL REFERENCES live_exam_questions (id),
    confirmed_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (participant_id, question_id)
);

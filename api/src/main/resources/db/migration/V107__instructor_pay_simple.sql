-- Instructor pay made simple (24 Nov, docs/exam-site/instructor-pay-simple-plan.md). Pay no longer comes from the classes: the Master
-- Admin records each payment by hand. Every new payment is a row of offline_instructor_other_payments, which gains what it was for
-- (lecture, exam or other) and, for the record only, how many classes it covers. The reason (the description) is required only for
-- OTHER, so the column may be empty. Rows from before keep their reason and are OTHER.
--
-- The class payment, not-taken and rate tables, and the pay columns of offline_classes, stay as they are: they hold history that the
-- payment history and Finance still show.

ALTER TABLE offline_instructor_other_payments ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'OTHER';
ALTER TABLE offline_instructor_other_payments ADD CONSTRAINT offline_instructor_other_payments_kind_check
    CHECK (kind IN ('LECTURE', 'EXAM', 'OTHER'));

ALTER TABLE offline_instructor_other_payments ADD COLUMN class_count INTEGER NULL;
ALTER TABLE offline_instructor_other_payments ADD CONSTRAINT offline_instructor_other_payments_class_count_check
    CHECK (class_count IS NULL OR class_count BETWEEN 1 AND 200);

ALTER TABLE offline_instructor_other_payments ALTER COLUMN reason DROP NOT NULL;

CREATE INDEX offline_instructor_other_payments_paid_on_idx ON offline_instructor_other_payments (paid_on DESC);

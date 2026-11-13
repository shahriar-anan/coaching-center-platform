-- Offline routines, stage 2 (plan docs/offline-admission/offline-instructor-pay-plan.md, section 5, step 3): the length of a class in
-- minutes, used when a routine gives only a start time ("5.10 PM"). Existing batches get the plan's default of 80 minutes. No existing
-- row is changed otherwise.

ALTER TABLE offline_batches ADD COLUMN default_class_minutes INTEGER NOT NULL DEFAULT 80;
ALTER TABLE offline_batches ADD CONSTRAINT offline_batches_class_minutes_check CHECK (default_class_minutes BETWEEN 15 AND 300);

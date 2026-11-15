-- Roll numbers (docs/offline-admission/offline-results-plan.md, rules R4, R5 and O2). A roll is digits only, 4 to 12 long, and unique
-- across every admission, so no two students ever share one. A batch may name its starting roll.
ALTER TABLE offline_admissions ADD COLUMN roll_no VARCHAR(12);
ALTER TABLE offline_admissions ADD CONSTRAINT offline_admissions_roll_no_check
    CHECK (roll_no IS NULL OR roll_no ~ '^[0-9]{4,12}$');
CREATE UNIQUE INDEX offline_admissions_roll_no_key ON offline_admissions (roll_no) WHERE roll_no IS NOT NULL;

ALTER TABLE offline_batches ADD COLUMN roll_start VARCHAR(12);
ALTER TABLE offline_batches ADD CONSTRAINT offline_batches_roll_start_check
    CHECK (roll_start IS NULL OR roll_start ~ '^[0-9]{4,12}$');

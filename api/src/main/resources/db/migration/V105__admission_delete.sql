-- Delete a student from a batch (four-fixes-16-nov-plan.md, issue 2). The Master Admin deletes an admission so its roll can be given
-- again. An admission with no payments and no results is removed with its rows. One with payments or results stays as a closed stub
-- so Finance and published results are unchanged: it is marked deleted, leaves every list, and gives up its roll (kept here).
ALTER TABLE offline_admissions ADD COLUMN deleted_at TIMESTAMPTZ NULL;
ALTER TABLE offline_admissions ADD COLUMN deleted_by UUID NULL REFERENCES users (id);
ALTER TABLE offline_admissions ADD COLUMN deleted_reason VARCHAR(500) NULL;
ALTER TABLE offline_admissions ADD COLUMN released_roll VARCHAR(12) NULL;

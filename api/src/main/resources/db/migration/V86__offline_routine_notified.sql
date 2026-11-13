-- Offline admission, stage 4 (plan section 6.3, the batch routine): how many onboarded students the last routine upload notified.
-- Nullable on purpose. A batch whose routine was uploaded before this migration keeps null, which the API shows as "not known",
-- and no existing row is changed. Only the upload that runs after this migration fills it in.

ALTER TABLE offline_batches ADD COLUMN routine_students_notified INTEGER NULL;

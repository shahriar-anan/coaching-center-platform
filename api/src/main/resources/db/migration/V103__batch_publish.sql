-- Batches are drafts until the Master Admin publishes them (15 Nov). A draft is hidden from students and is not offered when
-- setting up an admission; students already admitted keep seeing their batch. A published batch's form is locked until it is moved
-- back to draft. Every batch that exists now is published, so nothing changes for students. The previous release ignores the columns.
ALTER TABLE offline_batches ADD COLUMN published_at TIMESTAMPTZ NULL;
ALTER TABLE offline_batches ADD COLUMN published_by UUID NULL;
UPDATE offline_batches SET published_at = created_at;

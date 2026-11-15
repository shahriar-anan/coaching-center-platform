-- WhatsApp group or channel links (15 Nov). Admins set one per batch and per package; only students onboarded to the batch, or who
-- hold live access to a published package, are given the link. Nothing else changes.
ALTER TABLE offline_batches ADD COLUMN whatsapp_url VARCHAR(500);
ALTER TABLE live_exam_modules ADD COLUMN whatsapp_url VARCHAR(500);

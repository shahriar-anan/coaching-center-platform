-- Routines count from the day they are uploaded, not from a month (decision of 13 Nov 2026, offline-instructor-pay-plan.md section 0).
-- A routine may list earlier dates: they still become classes, but a new upload only replaces the classes from its own day onward.
-- So a batch has one live (CONFIRMED) routine, the latest; the earlier ones are SUPERSEDED and stay as the record.

ALTER TABLE offline_batch_routines ADD COLUMN effective_from DATE NULL;

-- The routines read so far were monthly: each counted from the first day of its month.
UPDATE offline_batch_routines SET effective_from = month;

ALTER TABLE offline_batch_routines ALTER COLUMN effective_from SET NOT NULL;

DROP INDEX offline_batch_routines_confirmed_key;

-- Keep the latest confirmed routine of each batch live, and supersede the others.
UPDATE offline_batch_routines r
SET status = 'SUPERSEDED'
WHERE r.status = 'CONFIRMED'
  AND EXISTS (
      SELECT 1
      FROM offline_batch_routines n
      WHERE n.batch_id = r.batch_id
        AND n.status = 'CONFIRMED'
        AND n.id <> r.id
        AND (n.effective_from, COALESCE(n.confirmed_at, n.uploaded_at), n.id)
            > (r.effective_from, COALESCE(r.confirmed_at, r.uploaded_at), r.id)
  );

CREATE UNIQUE INDEX offline_batch_routines_live_key ON offline_batch_routines (batch_id) WHERE status = 'CONFIRMED';
CREATE INDEX offline_batch_routines_batch_from_idx ON offline_batch_routines (batch_id, effective_from);

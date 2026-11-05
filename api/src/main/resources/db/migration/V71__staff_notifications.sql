-- Staff notification centre (staff pay plan, phase 1). A new table only: no existing table or row is changed.
-- One row per recipient. The kinds are a fixed list in code, so a new kind needs no migration.
-- dedupe_key is unique across the table, so a producer includes the recipient in the key it builds.
CREATE TABLE staff_notifications (
    id           UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind         VARCHAR(40) NOT NULL,
    payload      JSONB NOT NULL DEFAULT '{}'::jsonb,
    link         VARCHAR(300) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ NULL,
    dedupe_key   VARCHAR(200) NULL,
    CONSTRAINT staff_notifications_dedupe_key_key UNIQUE (dedupe_key)
);

CREATE INDEX staff_notifications_recipient_idx ON staff_notifications (recipient_id, read_at, created_at DESC);

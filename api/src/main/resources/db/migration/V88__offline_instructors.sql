-- Offline instructors and their pay rate per batch (plan docs/offline-admission/offline-instructor-pay-plan.md, sections 4.1 and F5, F7).
-- New tables only: no existing table or row is changed. An offline instructor is a name kept for the record; they have no account.

CREATE TABLE offline_instructors (
    id            UUID PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    phone         VARCHAR(20) NULL,
    note          VARCHAR(500) NULL,
    aliases       TEXT[] NOT NULL DEFAULT '{}',
    archived_at   TIMESTAMPTZ NULL,
    created_by    UUID NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT offline_instructors_name_check CHECK (btrim(name) <> '')
);

-- Names are unique among the instructors that are not archived, ignoring case.
CREATE UNIQUE INDEX offline_instructors_name_key ON offline_instructors (lower(name)) WHERE archived_at IS NULL;

-- The default rate of an instructor within one batch. A class takes it unless it has its own override.
CREATE TABLE offline_batch_instructor_rates (
    batch_id       UUID NOT NULL REFERENCES offline_batches (id),
    instructor_id  UUID NOT NULL REFERENCES offline_instructors (id),
    rate           NUMERIC(12,2) NOT NULL,
    updated_by     UUID NULL REFERENCES users (id),
    updated_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (batch_id, instructor_id),
    CONSTRAINT offline_batch_instructor_rates_rate_check CHECK (rate >= 0)
);

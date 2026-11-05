-- Public notice board (refunds, stats and notice board plan, section 6.1). Admins draft notices; the Master Admin publishes.
-- A published notice carries all four texts (English and Bangla title and description), checked here as well as in the service.
CREATE TABLE site_notices (
    id           UUID PRIMARY KEY,
    title_en     VARCHAR(300) NULL,
    title_bn     VARCHAR(300) NULL,
    body_en      TEXT NULL,
    body_bn      TEXT NULL,
    status       VARCHAR(16) NOT NULL,
    created_by   UUID NULL REFERENCES users (id),
    updated_by   UUID NULL REFERENCES users (id),
    published_by UUID NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    deleted_at   TIMESTAMPTZ NULL,
    CONSTRAINT site_notices_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'UNPUBLISHED')),
    CONSTRAINT site_notices_published_check CHECK (
        status <> 'PUBLISHED'
        OR (title_en IS NOT NULL AND title_bn IS NOT NULL AND body_en IS NOT NULL AND body_bn IS NOT NULL
            AND published_at IS NOT NULL)
    )
);

CREATE INDEX site_notices_published_idx ON site_notices (status, published_at DESC)
    WHERE deleted_at IS NULL;

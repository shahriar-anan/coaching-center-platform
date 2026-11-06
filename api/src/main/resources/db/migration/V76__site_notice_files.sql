-- Photos and the PDF of a public notice (notice media plan, F1). Optional: a notice with no rows here shows text only.
CREATE TABLE site_notice_files (
    id                 UUID PRIMARY KEY,
    notice_id          UUID NOT NULL REFERENCES site_notices (id),
    kind               VARCHAR(8) NOT NULL,
    position           SMALLINT NOT NULL DEFAULT 0,
    stored_name        VARCHAR(64) NOT NULL,
    original_name      VARCHAR(255) NULL,
    content_type       VARCHAR(64) NOT NULL,
    byte_size          BIGINT NOT NULL,
    created_by         UUID NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT site_notice_files_kind_check CHECK (kind IN ('PHOTO', 'PDF')),
    CONSTRAINT site_notice_files_position_check CHECK (position BETWEEN 0 AND 5)
);

CREATE INDEX site_notice_files_notice_idx ON site_notice_files (notice_id, kind, position);
CREATE UNIQUE INDEX site_notice_files_one_pdf_idx ON site_notice_files (notice_id) WHERE kind = 'PDF';

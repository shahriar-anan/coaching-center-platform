-- A package's optional routine PDF (notice media plan, F3). The generated routine table is unchanged.
CREATE TABLE live_exam_module_routines (
    module_id      UUID PRIMARY KEY REFERENCES live_exam_modules (id),
    stored_name    VARCHAR(64) NOT NULL,
    original_name  VARCHAR(255) NULL,
    byte_size      BIGINT NOT NULL,
    uploaded_by    UUID NULL REFERENCES users (id),
    uploaded_at    TIMESTAMPTZ NOT NULL
);

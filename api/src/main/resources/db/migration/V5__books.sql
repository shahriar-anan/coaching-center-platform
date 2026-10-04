CREATE TABLE books (
    id                  UUID PRIMARY KEY,
    title_en            VARCHAR(500) NOT NULL,
    title_bn            VARCHAR(500) NOT NULL,
    author              VARCHAR(500) NOT NULL,
    description         TEXT NOT NULL,
    price               NUMERIC(12, 2) NULL,
    available           BOOLEAN NOT NULL DEFAULT FALSE,
    cover_file_id       UUID NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

ALTER TABLE materials
    ALTER COLUMN course_id DROP NOT NULL;

ALTER TABLE materials
    ADD COLUMN book_id UUID NULL REFERENCES books (id);

ALTER TABLE books
    ADD CONSTRAINT books_cover_file_id_fkey FOREIGN KEY (cover_file_id) REFERENCES materials (id);

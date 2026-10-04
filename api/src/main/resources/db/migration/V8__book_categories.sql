CREATE TABLE book_categories (
    id          UUID PRIMARY KEY,
    title_en    VARCHAR(500) NOT NULL,
    title_bn    VARCHAR(500) NOT NULL,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL
);

CREATE INDEX book_categories_position_idx ON book_categories (position)
    WHERE deleted_at IS NULL;

ALTER TABLE books
    ADD COLUMN category_id UUID NULL REFERENCES book_categories (id);

CREATE INDEX books_category_idx ON books (category_id)
    WHERE deleted_at IS NULL AND category_id IS NOT NULL;

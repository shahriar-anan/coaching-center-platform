CREATE TABLE course_categories (
    id          UUID PRIMARY KEY,
    parent_id   UUID NULL REFERENCES course_categories (id),
    title_en    VARCHAR(500) NOT NULL,
    title_bn    VARCHAR(500) NOT NULL,
    position    INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ NULL
);

CREATE INDEX course_categories_parent_idx ON course_categories (parent_id, position)
    WHERE deleted_at IS NULL;

ALTER TABLE courses
    ADD COLUMN category_id UUID NULL REFERENCES course_categories (id),
    ADD COLUMN category_position INTEGER NULL,
    ADD CONSTRAINT courses_category_placement_check CHECK (
        (category_id IS NULL AND category_position IS NULL)
        OR (category_id IS NOT NULL AND category_position IS NOT NULL)
    );

CREATE INDEX courses_category_idx ON courses (category_id, category_position)
    WHERE deleted_at IS NULL AND category_id IS NOT NULL;

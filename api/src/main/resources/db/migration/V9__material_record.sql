ALTER TABLE materials
    ADD COLUMN title VARCHAR(500),
    ADD COLUMN description TEXT,
    ADD COLUMN kind VARCHAR(16),
    ADD COLUMN position INTEGER,
    ADD COLUMN section_node_id UUID NULL REFERENCES content_nodes (id);

UPDATE materials
SET title = original_file_name,
    kind = 'OTHER';

WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY course_id, book_id, lecture_node_id
               ORDER BY created_at ASC
           ) - 1 AS pos
    FROM materials
)
UPDATE materials AS material
SET position = ranked.pos
FROM ranked
WHERE material.id = ranked.id;

ALTER TABLE materials
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN kind SET NOT NULL,
    ALTER COLUMN position SET NOT NULL;

ALTER TABLE materials
    ADD CONSTRAINT materials_kind_check CHECK (kind IN ('HANDOUT', 'EXERCISE', 'SLIDE', 'OTHER')),
    ADD CONSTRAINT materials_one_node_check CHECK (lecture_node_id IS NULL OR section_node_id IS NULL);

CREATE INDEX materials_section_idx ON materials (section_node_id, position)
    WHERE deleted_at IS NULL AND section_node_id IS NOT NULL;

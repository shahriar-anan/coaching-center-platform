-- A package (module) has one optional cover image, uploaded by the Master Admin.
-- The stored file lives in the same file store as the question images; the row keeps its name and type.
ALTER TABLE live_exam_modules ADD COLUMN cover_stored_name varchar(80);
ALTER TABLE live_exam_modules ADD COLUMN cover_content_type varchar(40);
ALTER TABLE live_exam_modules ADD COLUMN cover_byte_size bigint;
ALTER TABLE live_exam_modules ADD COLUMN cover_updated_at timestamptz;

-- Student page Phase 4 (E1): an exam has one optional cover image, uploaded by the Master Admin.
-- The same four columns as a package cover (V41); the file lives in the same image store.
ALTER TABLE live_exams ADD COLUMN cover_stored_name varchar(80);
ALTER TABLE live_exams ADD COLUMN cover_content_type varchar(40);
ALTER TABLE live_exams ADD COLUMN cover_byte_size bigint;
ALTER TABLE live_exams ADD COLUMN cover_updated_at timestamptz;

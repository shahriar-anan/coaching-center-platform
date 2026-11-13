-- Offline admission, stage 3 (plan section 7): the values an admission mail was written with.
-- The mail text is built when the scan sends it, after the admission may have changed. The terms-change mail must still show
-- the old and new values it was written with, and the approval and close mails the values at that moment. No column in
-- exam_outbound_mail holds them, so this one nullable column is added. Existing rows keep null, and no row is changed.

ALTER TABLE exam_outbound_mail ADD COLUMN payload TEXT NULL;

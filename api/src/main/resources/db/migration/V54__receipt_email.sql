-- Student page Phase 4B (R2): the address a student typed for the receipt of one order. Null means none was typed, so the
-- receipt goes to the account email only when that email is verified (see ExamMailTexts.receiptAddress). Not saved to the profile.
ALTER TABLE live_exam_orders ADD COLUMN receipt_email varchar(254);

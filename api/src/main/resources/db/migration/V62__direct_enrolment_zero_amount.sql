-- A direct enrolment (access granted by hand) is recorded as a paid order of no money, so an amount of zero is allowed for it only.
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_amount_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_amount_check CHECK (amount > 0 OR (method = 'DIRECT' AND amount = 0));

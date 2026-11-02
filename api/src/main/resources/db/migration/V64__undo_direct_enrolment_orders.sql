-- Undoes the direct-enrolment orders (V62, V63). A direct enrolment is shown on the exam's enrolled list and in notices, not in orders.
UPDATE live_exam_grants SET order_id = NULL WHERE order_id IN (SELECT id FROM live_exam_orders WHERE method = 'DIRECT');
DELETE FROM live_exam_orders WHERE method = 'DIRECT';
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_amount_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_amount_check CHECK (amount > 0);
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_method_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_method_check CHECK (method = 'SSLCOMMERZ');

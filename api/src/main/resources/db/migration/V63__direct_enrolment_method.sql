-- A direct enrolment is recorded with the method DIRECT; the other orders still use SSLCOMMERZ.
ALTER TABLE live_exam_orders DROP CONSTRAINT live_exam_orders_method_check;
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_method_check CHECK (method IN ('SSLCOMMERZ', 'DIRECT'));

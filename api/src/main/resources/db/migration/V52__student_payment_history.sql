-- Student page Phase 4 (P2, P4): when an order was paid, and a receipt number for each paid order.
-- Paid time is set in the same transaction that marks an order SUCCEEDED. Old paid orders get their updated_at,
-- the best value on record, which is approximate when the order changed after payment.
ALTER TABLE live_exam_orders ADD COLUMN paid_at timestamptz;
ALTER TABLE live_exam_orders ADD COLUMN receipt_no varchar(24);
ALTER TABLE live_exam_orders ADD CONSTRAINT live_exam_orders_receipt_no_key UNIQUE (receipt_no);

CREATE SEQUENCE live_exam_receipt_seq START WITH 1;

UPDATE live_exam_orders SET paid_at = updated_at WHERE status = 'SUCCEEDED' AND paid_at IS NULL;

-- Existing paid orders take receipt numbers in paid order. The year is the paid year in Bangladesh time.
WITH numbered AS (
    SELECT id, paid_at, row_number() OVER (ORDER BY paid_at, id) AS n
    FROM live_exam_orders
    WHERE status = 'SUCCEEDED'
)
UPDATE live_exam_orders o
SET receipt_no = 'RCPT-' || to_char(numbered.paid_at AT TIME ZONE 'Asia/Dhaka', 'YYYY') || '-' || lpad(numbered.n::text, 6, '0')
FROM numbered
WHERE numbered.id = o.id;

SELECT setval('live_exam_receipt_seq',
    GREATEST((SELECT count(*) FROM live_exam_orders WHERE receipt_no IS NOT NULL), 1),
    (SELECT count(*) FROM live_exam_orders WHERE receipt_no IS NOT NULL) > 0);

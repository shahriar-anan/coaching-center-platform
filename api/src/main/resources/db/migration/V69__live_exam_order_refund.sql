-- A manual refund, recorded by the Master Admin after the refund is made in the SSLCommerz merchant panel (plan section 5).
-- The site never calls SSLCommerz's refund API. The order keeps status SUCCEEDED, so receipts, paid dates and reports keep working.
-- "Refunded" is derived from refunded_at. Only adds columns and constraints; no row is changed.
ALTER TABLE live_exam_orders
    ADD COLUMN refunded_at TIMESTAMPTZ NULL,
    ADD COLUMN refunded_by UUID NULL REFERENCES users (id),
    ADD COLUMN refund_amount NUMERIC(12,2) NULL,
    ADD COLUMN refund_reason VARCHAR(500) NULL,
    ADD COLUMN refund_reference VARCHAR(128) NULL,
    ADD CONSTRAINT live_exam_orders_refund_amount_check CHECK (refund_amount IS NULL OR (refund_amount >= 0.01 AND refund_amount <= amount)),
    ADD CONSTRAINT live_exam_orders_refund_reason_check CHECK (refund_reason IS NULL OR length(btrim(refund_reason)) > 0),
    ADD CONSTRAINT live_exam_orders_refund_set_check CHECK (
        (refunded_at IS NULL AND refunded_by IS NULL AND refund_amount IS NULL AND refund_reason IS NULL)
        OR (refunded_at IS NOT NULL AND refunded_by IS NOT NULL AND refund_amount IS NOT NULL AND refund_reason IS NOT NULL)
    ),
    ADD CONSTRAINT live_exam_orders_refund_status_check CHECK (refunded_at IS NULL OR status = 'SUCCEEDED');

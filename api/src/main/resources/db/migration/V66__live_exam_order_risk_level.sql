-- SSLCommerz's risk flag from the validation reply ("0" or "1"). Null before V66 and until a verified success.
-- Such a payment is still granted; the Master Admin sees the flag on the order list and reviews it in the merchant panel.
ALTER TABLE live_exam_orders
    ADD COLUMN risk_level VARCHAR(16) NULL;

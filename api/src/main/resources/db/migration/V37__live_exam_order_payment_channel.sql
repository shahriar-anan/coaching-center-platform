-- The payer's channel from the SSLCommerz validation reply, for example BKASH-BKash. Null before V37 and until success.
ALTER TABLE live_exam_orders
    ADD COLUMN payment_channel VARCHAR(64) NULL;

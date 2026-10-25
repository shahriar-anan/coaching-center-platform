-- One email to the Master Admin per successful exam purchase. Key: the order id.
ALTER TABLE exam_outbound_mail DROP CONSTRAINT exam_outbound_mail_kind_check;
ALTER TABLE exam_outbound_mail ADD CONSTRAINT exam_outbound_mail_kind_check CHECK (
    kind IN ('PAYMENT_RECEIPT', 'EXAM_TIME', 'HOUR_BEFORE', 'REFUND', 'PURCHASE_NOTICE')
);

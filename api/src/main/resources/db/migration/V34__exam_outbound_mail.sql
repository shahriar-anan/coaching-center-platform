CREATE TABLE exam_outbound_mail (
    id             UUID PRIMARY KEY,
    kind           VARCHAR(32) NOT NULL,
    message_key    VARCHAR(220) NOT NULL,
    user_id        UUID NOT NULL REFERENCES users (id),
    attempt_count  INTEGER NOT NULL,
    status         VARCHAR(16) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT exam_outbound_mail_kind_check CHECK (
        kind IN ('PAYMENT_RECEIPT', 'EXAM_TIME', 'HOUR_BEFORE', 'REFUND')
    ),
    CONSTRAINT exam_outbound_mail_status_check CHECK (
        status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')
    ),
    CONSTRAINT exam_outbound_mail_attempt_check CHECK (attempt_count >= 0),
    CONSTRAINT exam_outbound_mail_key_unique UNIQUE (kind, message_key)
);

CREATE INDEX exam_outbound_mail_pending_idx ON exam_outbound_mail (created_at) WHERE status = 'PENDING';

-- Payout accounts for System Admins and instructors (staff pay plan, phase 2). A new table only: no existing table or row
-- is changed. Each person owns their own accounts; the Master Admin reads them and never writes them.
-- A bank account carries all five bank fields and no wallet number; a wallet carries its number and no bank fields.
CREATE TABLE staff_payout_accounts (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            VARCHAR(16) NOT NULL,
    account_name    VARCHAR(120) NULL,
    account_number  VARCHAR(20) NULL,
    bank_name       VARCHAR(120) NULL,
    branch          VARCHAR(120) NULL,
    routing_number  VARCHAR(9) NULL,
    wallet_number   VARCHAR(16) NULL,
    preferred       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ NULL,
    CONSTRAINT staff_payout_accounts_kind_check CHECK (kind IN ('BANK', 'BKASH', 'NAGAD', 'ROCKET')),
    CONSTRAINT staff_payout_accounts_shape_check CHECK (
        (kind = 'BANK' AND account_name IS NOT NULL AND account_number IS NOT NULL AND bank_name IS NOT NULL
            AND branch IS NOT NULL AND routing_number IS NOT NULL)
        OR (kind <> 'BANK' AND wallet_number IS NOT NULL)
    )
);

-- At most one preferred live account per person.
CREATE UNIQUE INDEX staff_payout_accounts_preferred_key ON staff_payout_accounts (user_id)
    WHERE preferred AND deleted_at IS NULL;

CREATE INDEX staff_payout_accounts_user_idx ON staff_payout_accounts (user_id, created_at)
    WHERE deleted_at IS NULL;

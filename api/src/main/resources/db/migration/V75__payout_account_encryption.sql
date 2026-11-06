-- Phase 9 of the staff pay plan: encrypted copies of the payout account numbers (decision recorded in
-- docs/exam-site/plans/staff-pay.md, Phase 9). Adds nullable columns only. No existing column, row or value changes here.
-- The plain columns (account_number, routing_number, wallet_number) stay until the verification step has passed and a
-- later migration removes them.
ALTER TABLE staff_payout_accounts
    ADD COLUMN account_number_enc BYTEA NULL,
    ADD COLUMN routing_number_enc BYTEA NULL,
    ADD COLUMN wallet_number_enc BYTEA NULL,
    ADD COLUMN key_version SMALLINT NULL;

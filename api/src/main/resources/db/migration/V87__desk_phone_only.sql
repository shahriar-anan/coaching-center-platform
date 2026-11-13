-- Offline admission, desk by phone only (product decision, 13 November 2026).
-- The desk starts an admission from a phone number alone, so the account has no email until the student's form is submitted.
-- The unique index users_email_active_idx allows many NULLs, so the column only loses its NOT NULL.

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- The activation code the desk can read out again. It is kept as plain text only so the desk can show it within its 10 minutes;
-- the one_time_codes row keeps the hash that the activation checks. The plain code is cleared on activation and on expiry.
ALTER TABLE offline_admissions ADD COLUMN activation_code VARCHAR(16) NULL;
ALTER TABLE offline_admissions ADD COLUMN activation_code_expires_at TIMESTAMPTZ NULL;
ALTER TABLE offline_admissions ADD COLUMN activation_code_issued_by UUID NULL REFERENCES users (id);
ALTER TABLE offline_admissions ADD COLUMN activated_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN offline_admissions.activation_code IS
    'Plain activation code, kept only so the desk can read it out within its 10 minutes. Cleared on activation and on expiry.';

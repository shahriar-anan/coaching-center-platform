-- Staff pay ledger (staff pay plan, phase 3). New tables only: no existing table or row is changed.
-- Money is NUMERIC(12,2). The balance is the live MARKING, SALARY and ADJUSTMENT amounts minus the live PAYMENT amounts;
-- a voided row (voided_at set) leaves the balance but stays in the table, so nothing is ever deleted.

-- One row only. id is pinned to 1 so the table cannot hold a second set of defaults.
CREATE TABLE staff_pay_defaults (
    id                      SMALLINT PRIMARY KEY DEFAULT 1,
    sheet_rate              NUMERIC(12,2) NOT NULL DEFAULT 50.00,
    unpaid_reminder_days    INTEGER NOT NULL DEFAULT 14,
    salary_due_soon_days    INTEGER NOT NULL DEFAULT 3,
    updated_by              UUID NULL REFERENCES users (id),
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT staff_pay_defaults_singleton_check CHECK (id = 1),
    CONSTRAINT staff_pay_defaults_sheet_rate_check CHECK (sheet_rate > 0),
    CONSTRAINT staff_pay_defaults_unpaid_days_check CHECK (unpaid_reminder_days BETWEEN 1 AND 365),
    CONSTRAINT staff_pay_defaults_due_soon_check CHECK (salary_due_soon_days BETWEEN 0 AND 30)
);

INSERT INTO staff_pay_defaults (id, sheet_rate, unpaid_reminder_days, salary_due_soon_days, updated_at)
VALUES (1, 50.00, 14, 3, now());

-- Per person, only for people the Master Admin has set up. Salary fields are for a System Admin; sheet_rate is an
-- instructor's override of the default.
CREATE TABLE staff_pay_settings (
    user_id          UUID PRIMARY KEY REFERENCES users (id),
    monthly_salary   NUMERIC(12,2) NULL,
    pay_day          SMALLINT NULL,
    salary_start     DATE NULL,
    salary_end       DATE NULL,
    sheet_rate       NUMERIC(12,2) NULL,
    updated_by       UUID NULL REFERENCES users (id),
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT staff_pay_settings_salary_check CHECK (monthly_salary IS NULL OR monthly_salary > 0),
    CONSTRAINT staff_pay_settings_pay_day_check CHECK (pay_day IS NULL OR pay_day BETWEEN 1 AND 28),
    CONSTRAINT staff_pay_settings_salary_set_check CHECK (
        monthly_salary IS NULL OR (salary_start IS NOT NULL AND pay_day IS NOT NULL)),
    CONSTRAINT staff_pay_settings_salary_end_check CHECK (
        salary_end IS NULL OR salary_start IS NULL OR salary_end >= salary_start),
    CONSTRAINT staff_pay_settings_sheet_rate_check CHECK (sheet_rate IS NULL OR sheet_rate > 0)
);

-- Every money line. Columns by kind (plan section 3):
--   MARKING     participant_id (unique), exam_id, sheet_rate. amount = sheet_rate; due_on = the publish date (Dhaka).
--   SALARY      period ('2026-11'), unique per (user_id, period). due_on = the pay day of the following month.
--   ADJUSTMENT  note (required). amount may be negative. due_on = the day it was recorded (Dhaka).
--   PAYMENT     paid_on, method, account_snapshot (masked text, never the full number), reference, note. amount positive;
--               due_on = paid_on.
CREATE TABLE staff_ledger (
    id                 UUID PRIMARY KEY,
    user_id            UUID NOT NULL REFERENCES users (id),
    kind               VARCHAR(16) NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    due_on             DATE NOT NULL,
    participant_id     UUID NULL,
    exam_id            UUID NULL,
    sheet_rate         NUMERIC(12,2) NULL,
    period             VARCHAR(7) NULL,
    note               VARCHAR(500) NULL,
    paid_on            DATE NULL,
    method             VARCHAR(16) NULL,
    account_snapshot   VARCHAR(200) NULL,
    reference          VARCHAR(100) NULL,
    created_by         UUID NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    voided_at          TIMESTAMPTZ NULL,
    voided_by          UUID NULL REFERENCES users (id),
    void_reason        VARCHAR(500) NULL,
    CONSTRAINT staff_ledger_kind_check CHECK (kind IN ('MARKING', 'SALARY', 'ADJUSTMENT', 'PAYMENT')),
    CONSTRAINT staff_ledger_amount_check CHECK (
        (kind IN ('MARKING', 'SALARY', 'PAYMENT') AND amount > 0)
        OR (kind = 'ADJUSTMENT' AND amount <> 0)),
    CONSTRAINT staff_ledger_participant_key UNIQUE (participant_id),
    CONSTRAINT staff_ledger_period_key UNIQUE (user_id, period),
    CONSTRAINT staff_ledger_marking_check CHECK (
        kind <> 'MARKING' OR (participant_id IS NOT NULL AND exam_id IS NOT NULL AND sheet_rate IS NOT NULL)),
    CONSTRAINT staff_ledger_salary_check CHECK (kind <> 'SALARY' OR period IS NOT NULL),
    CONSTRAINT staff_ledger_adjustment_check CHECK (kind <> 'ADJUSTMENT' OR note IS NOT NULL),
    CONSTRAINT staff_ledger_payment_check CHECK (kind <> 'PAYMENT' OR (paid_on IS NOT NULL AND method IS NOT NULL)),
    CONSTRAINT staff_ledger_method_check CHECK (method IS NULL OR method IN ('BANK', 'BKASH', 'NAGAD', 'ROCKET')),
    CONSTRAINT staff_ledger_void_check CHECK (
        (voided_at IS NULL) = (voided_by IS NULL) AND (voided_at IS NULL) = (void_reason IS NULL)),
    CONSTRAINT staff_ledger_voidable_check CHECK (voided_at IS NULL OR kind IN ('PAYMENT', 'ADJUSTMENT'))
);

CREATE INDEX staff_ledger_user_idx ON staff_ledger (user_id, created_at DESC);

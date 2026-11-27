-- Accounting, part 1: the schema and the seed (docs/accounting/accounting-plan.md, sections 5.2 and 5.3). New tables only; no existing
-- table is changed. The old instructor payments move into these tables in V107_2. Every table prefix is acc_.
--
-- Money is NUMERIC(12,2), quantities NUMERIC(10,2). A deduction charge is stored negative. The seeded rows use fixed ids, which the
-- Java constants in AccIds.java repeat.

CREATE SEQUENCE acc_entry_no_seq START WITH 1;

-- Groups (khat): the places money goes out to or comes in from. Payees groups hold people with balances; Log groups hold a plain list.
CREATE TABLE acc_groups (
    id            UUID PRIMARY KEY,
    name_en       VARCHAR(100) NOT NULL,
    name_bn       VARCHAR(100) NULL,
    mode          VARCHAR(16) NOT NULL,
    direction     VARCHAR(16) NOT NULL,
    system_key    VARCHAR(32) NULL,
    in_finance    BOOLEAN NOT NULL,
    position      INTEGER NOT NULL,
    archived_at   TIMESTAMPTZ NULL,
    created_by    UUID NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    version       BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acc_groups_name_check CHECK (btrim(name_en) <> ''),
    CONSTRAINT acc_groups_mode_check CHECK (mode IN ('PAYEES', 'LOG')),
    CONSTRAINT acc_groups_direction_check CHECK (direction IN ('EXPENSE', 'INCOME')),
    CONSTRAINT acc_groups_system_key_check CHECK (
        system_key IS NULL OR system_key IN ('OFFLINE_INSTRUCTORS', 'STAFF', 'SUPPLIERS', 'PERSONAL', 'OTHER_INCOME')),
    CONSTRAINT acc_groups_income_is_log_check CHECK (direction = 'EXPENSE' OR mode = 'LOG')
);

CREATE UNIQUE INDEX acc_groups_system_key_key ON acc_groups (system_key);
CREATE UNIQUE INDEX acc_groups_name_key ON acc_groups (lower(name_en)) WHERE archived_at IS NULL;

-- Types (dhoron): the sub-categories of one group. A counted type has a unit and is quantity times price.
CREATE TABLE acc_types (
    id                 UUID PRIMARY KEY,
    group_id           UUID NOT NULL REFERENCES acc_groups (id),
    name_en            VARCHAR(100) NOT NULL,
    name_bn            VARCHAR(100) NULL,
    kind               VARCHAR(16) NOT NULL,
    unit_en            VARCHAR(30) NULL,
    unit_bn            VARCHAR(30) NULL,
    needs_period       BOOLEAN NOT NULL,
    needs_description  BOOLEAN NOT NULL,
    position           INTEGER NOT NULL,
    archived_at        TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acc_types_name_check CHECK (btrim(name_en) <> ''),
    CONSTRAINT acc_types_kind_check CHECK (kind IN ('COUNTED', 'AMOUNT', 'DEDUCTION')),
    CONSTRAINT acc_types_counted_unit_check CHECK (kind <> 'COUNTED' OR unit_en IS NOT NULL)
);

CREATE UNIQUE INDEX acc_types_name_key ON acc_types (group_id, lower(name_en)) WHERE archived_at IS NULL;

-- Payees: a person or firm inside a Payees group, with a balance. An offline instructor's payee has the same id as its
-- offline_instructors row (F2 copies them).
CREATE TABLE acc_payees (
    id           UUID PRIMARY KEY,
    group_id     UUID NOT NULL REFERENCES acc_groups (id),
    name         VARCHAR(200) NOT NULL,
    phone        VARCHAR(20) NULL,
    note         VARCHAR(500) NULL,
    user_id      UUID NULL REFERENCES users (id),
    archived_at  TIMESTAMPTZ NULL,
    created_by   UUID NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acc_payees_name_check CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX acc_payees_name_key ON acc_payees (group_id, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX acc_payees_group_archived_idx ON acc_payees (group_id, archived_at);
-- A site user is linked to at most one live payee (plan 4.3). The service checks it too; this index is the last line.
CREATE UNIQUE INDEX acc_payees_live_user_key ON acc_payees (user_id) WHERE user_id IS NOT NULL AND archived_at IS NULL;

-- Money accounts: where the money came from or went to (Cash box, bKash, Nagad, Rocket, Bank).
CREATE TABLE acc_accounts (
    id           UUID PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    kind         VARCHAR(16) NOT NULL,
    note         VARCHAR(500) NULL,
    position     INTEGER NOT NULL,
    archived_at  TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acc_accounts_name_check CHECK (btrim(name) <> ''),
    CONSTRAINT acc_accounts_kind_check CHECK (kind IN ('CASH', 'BKASH', 'NAGAD', 'ROCKET', 'BANK'))
);

CREATE UNIQUE INDEX acc_accounts_name_key ON acc_accounts (lower(name)) WHERE archived_at IS NULL;

-- Entries: one ledger. A charge (owed), a payment (paid to a payee), a spending or an income. Nothing is deleted; a void keeps the row.
CREATE TABLE acc_entries (
    id               UUID PRIMARY KEY,
    no               BIGINT NOT NULL DEFAULT nextval('acc_entry_no_seq'),
    kind             VARCHAR(16) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    group_id         UUID NOT NULL REFERENCES acc_groups (id),
    payee_id         UUID NULL REFERENCES acc_payees (id),
    type_id          UUID NULL REFERENCES acc_types (id),
    entry_date       DATE NOT NULL,
    period           CHAR(7) NULL,
    quantity         NUMERIC(10,2) NULL,
    unit_price       NUMERIC(12,2) NULL,
    amount           NUMERIC(12,2) NOT NULL,
    account_id       UUID NULL REFERENCES acc_accounts (id),
    reference        VARCHAR(100) NULL,
    counterparty     VARCHAR(200) NULL,
    batch_id         UUID NULL REFERENCES offline_batches (id),
    live_exam_id     UUID NULL REFERENCES live_exams (id),
    description      VARCHAR(500) NULL,
    note             VARCHAR(500) NULL,
    paired_entry_id  UUID NULL REFERENCES acc_entries (id),
    legacy_source    VARCHAR(16) NULL,
    legacy_id        UUID NULL,
    legacy_class_id  UUID NULL REFERENCES offline_classes (id),
    recorded_by      UUID NOT NULL REFERENCES users (id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    corrected_at     TIMESTAMPTZ NULL,
    voided_by        UUID NULL REFERENCES users (id),
    voided_at        TIMESTAMPTZ NULL,
    void_reason      VARCHAR(500) NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT acc_entries_no_key UNIQUE (no),
    CONSTRAINT acc_entries_kind_check CHECK (kind IN ('CHARGE', 'PAYMENT', 'SPEND', 'INCOME')),
    CONSTRAINT acc_entries_status_check CHECK (status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT acc_entries_legacy_source_check CHECK (legacy_source IS NULL OR legacy_source IN ('CLASS', 'OTHER')),
    CONSTRAINT acc_entries_payee_check CHECK ((kind IN ('CHARGE', 'PAYMENT')) = (payee_id IS NOT NULL)),
    CONSTRAINT acc_entries_type_check CHECK ((kind <> 'PAYMENT') = (type_id IS NOT NULL)),
    CONSTRAINT acc_entries_account_check CHECK ((kind <> 'CHARGE') = (account_id IS NOT NULL)),
    CONSTRAINT acc_entries_amount_zero_check CHECK (amount <> 0),
    CONSTRAINT acc_entries_amount_sign_check CHECK (kind = 'CHARGE' OR amount > 0),
    CONSTRAINT acc_entries_quantity_check CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT acc_entries_unit_price_check CHECK (unit_price IS NULL OR unit_price > 0),
    CONSTRAINT acc_entries_price_needs_quantity_check CHECK (unit_price IS NULL OR quantity IS NOT NULL),
    CONSTRAINT acc_entries_period_check CHECK (period IS NULL OR period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT acc_entries_void_check CHECK (
        (status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL OR void_reason IS NOT NULL))
);

-- An old row moves once: one entry per (old table, old id). Charges have no legacy_id; only payments carry it.
CREATE UNIQUE INDEX acc_entries_legacy_key ON acc_entries (legacy_source, legacy_id) WHERE legacy_source IS NOT NULL;
CREATE INDEX acc_entries_payee_status_idx ON acc_entries (payee_id, status);
CREATE INDEX acc_entries_group_date_idx ON acc_entries (group_id, entry_date DESC);
CREATE INDEX acc_entries_date_idx ON acc_entries (entry_date DESC);
CREATE INDEX acc_entries_batch_idx ON acc_entries (batch_id) WHERE batch_id IS NOT NULL;
CREATE INDEX acc_entries_type_idx ON acc_entries (type_id);

-- Every correction of an entry, with the values before and after. The reason is required.
CREATE TABLE acc_entry_edits (
    id             UUID PRIMARY KEY,
    entry_id       UUID NOT NULL REFERENCES acc_entries (id),
    before_values  JSONB NOT NULL,
    after_values   JSONB NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    edited_by      UUID NOT NULL REFERENCES users (id),
    edited_at      TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT acc_entry_edits_reason_check CHECK (btrim(reason) <> '')
);

CREATE INDEX acc_entry_edits_entry_idx ON acc_entry_edits (entry_id, edited_at);

-- Seed. Fixed ids (see AccIds.java). Groups: 1 Offline instructors, 2 Staff, 3 Suppliers, 4 Personal, 5 Other income.
-- Types: group digit then position, e.g. ...0111 is the first type of group 1. Accounts: 1 Cash box to 5 Bank.
-- The seeded rows have no creator (created_by is empty).

INSERT INTO acc_groups (id, name_en, name_bn, mode, direction, system_key, in_finance, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Offline instructors', 'অফলাইন শিক্ষক', 'PAYEES', 'EXPENSE', 'OFFLINE_INSTRUCTORS', TRUE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000002', 'Staff', 'স্টাফ', 'PAYEES', 'EXPENSE', 'STAFF', TRUE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000003', 'Suppliers', 'সরবরাহকারী', 'PAYEES', 'EXPENSE', 'SUPPLIERS', TRUE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000004', 'Personal', 'ব্যক্তিগত', 'LOG', 'EXPENSE', 'PERSONAL', FALSE, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000005', 'Other income', 'অন্যান্য আয়', 'LOG', 'INCOME', 'OTHER_INCOME', TRUE, 5, now(), now());

-- Offline instructors: Lecture, Exam, Answer sheet evaluation, Question making, Bonus, Deduction, Other.
INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000111', 'a0000000-0000-4000-8000-000000000001', 'Lecture', 'ক্লাস', 'COUNTED', 'class', 'ক্লাস', FALSE, FALSE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000112', 'a0000000-0000-4000-8000-000000000001', 'Exam', 'পরীক্ষা', 'COUNTED', 'exam', 'পরীক্ষা', FALSE, FALSE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000113', 'a0000000-0000-4000-8000-000000000001', 'Answer sheet evaluation', 'খাতা দেখা', 'COUNTED', 'answer sheet', 'খাতা', FALSE, FALSE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000114', 'a0000000-0000-4000-8000-000000000001', 'Question making', 'প্রশ্ন তৈরি', 'COUNTED', 'question', 'প্রশ্ন', FALSE, FALSE, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000115', 'a0000000-0000-4000-8000-000000000001', 'Bonus', 'বোনাস', 'AMOUNT', NULL, NULL, FALSE, FALSE, 5, now(), now()),
    ('a0000000-0000-4000-8000-000000000116', 'a0000000-0000-4000-8000-000000000001', 'Deduction', 'কর্তন', 'DEDUCTION', NULL, NULL, FALSE, FALSE, 6, now(), now()),
    ('a0000000-0000-4000-8000-000000000117', 'a0000000-0000-4000-8000-000000000001', 'Other', 'অন্যান্য', 'AMOUNT', NULL, NULL, FALSE, TRUE, 7, now(), now());

-- Staff: Monthly salary, Bonus, Festival bonus, Overtime, Deduction, Other.
INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000121', 'a0000000-0000-4000-8000-000000000002', 'Monthly salary', 'মাসিক বেতন', 'AMOUNT', NULL, NULL, TRUE, FALSE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000122', 'a0000000-0000-4000-8000-000000000002', 'Bonus', 'বোনাস', 'AMOUNT', NULL, NULL, FALSE, FALSE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000123', 'a0000000-0000-4000-8000-000000000002', 'Festival bonus', 'উৎসব বোনাস', 'AMOUNT', NULL, NULL, FALSE, FALSE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000124', 'a0000000-0000-4000-8000-000000000002', 'Overtime', 'ওভারটাইম', 'COUNTED', 'hour', 'ঘণ্টা', FALSE, FALSE, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000125', 'a0000000-0000-4000-8000-000000000002', 'Deduction', 'কর্তন', 'DEDUCTION', NULL, NULL, FALSE, FALSE, 5, now(), now()),
    ('a0000000-0000-4000-8000-000000000126', 'a0000000-0000-4000-8000-000000000002', 'Other', 'অন্যান্য', 'AMOUNT', NULL, NULL, FALSE, TRUE, 6, now(), now());

-- Suppliers: Printing, Stationery, Advertising, Rent, Utilities, Repairs, Other.
INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000131', 'a0000000-0000-4000-8000-000000000003', 'Printing', 'ছাপা', 'AMOUNT', NULL, NULL, FALSE, FALSE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000132', 'a0000000-0000-4000-8000-000000000003', 'Stationery', 'স্টেশনারি', 'AMOUNT', NULL, NULL, FALSE, FALSE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000133', 'a0000000-0000-4000-8000-000000000003', 'Advertising', 'বিজ্ঞাপন', 'AMOUNT', NULL, NULL, FALSE, FALSE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000134', 'a0000000-0000-4000-8000-000000000003', 'Rent', 'ভাড়া', 'AMOUNT', NULL, NULL, TRUE, FALSE, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000135', 'a0000000-0000-4000-8000-000000000003', 'Utilities', 'ইউটিলিটি বিল', 'AMOUNT', NULL, NULL, TRUE, FALSE, 5, now(), now()),
    ('a0000000-0000-4000-8000-000000000136', 'a0000000-0000-4000-8000-000000000003', 'Repairs', 'মেরামত', 'AMOUNT', NULL, NULL, FALSE, FALSE, 6, now(), now()),
    ('a0000000-0000-4000-8000-000000000137', 'a0000000-0000-4000-8000-000000000003', 'Other', 'অন্যান্য', 'AMOUNT', NULL, NULL, FALSE, TRUE, 7, now(), now());

-- Personal: Food, Transport, Family, Health, Bills, Other.
INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000141', 'a0000000-0000-4000-8000-000000000004', 'Food', 'খাবার', 'AMOUNT', NULL, NULL, FALSE, FALSE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000142', 'a0000000-0000-4000-8000-000000000004', 'Transport', 'যাতায়াত', 'AMOUNT', NULL, NULL, FALSE, FALSE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000143', 'a0000000-0000-4000-8000-000000000004', 'Family', 'পরিবার', 'AMOUNT', NULL, NULL, FALSE, FALSE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000144', 'a0000000-0000-4000-8000-000000000004', 'Health', 'স্বাস্থ্য', 'AMOUNT', NULL, NULL, FALSE, FALSE, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000145', 'a0000000-0000-4000-8000-000000000004', 'Bills', 'বিল', 'AMOUNT', NULL, NULL, FALSE, FALSE, 5, now(), now()),
    ('a0000000-0000-4000-8000-000000000146', 'a0000000-0000-4000-8000-000000000004', 'Other', 'অন্যান্য', 'AMOUNT', NULL, NULL, FALSE, TRUE, 6, now(), now());

-- Other income: Book and sheet sales, Donation, Investment, Other.
INSERT INTO acc_types (id, group_id, name_en, name_bn, kind, unit_en, unit_bn, needs_period, needs_description, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000151', 'a0000000-0000-4000-8000-000000000005', 'Book and sheet sales', 'বই ও খাতা বিক্রি', 'AMOUNT', NULL, NULL, FALSE, FALSE, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000152', 'a0000000-0000-4000-8000-000000000005', 'Donation', 'অনুদান', 'AMOUNT', NULL, NULL, FALSE, FALSE, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000153', 'a0000000-0000-4000-8000-000000000005', 'Investment', 'বিনিয়োগ', 'AMOUNT', NULL, NULL, FALSE, FALSE, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000154', 'a0000000-0000-4000-8000-000000000005', 'Other', 'অন্যান্য', 'AMOUNT', NULL, NULL, FALSE, TRUE, 4, now(), now());

INSERT INTO acc_accounts (id, name, kind, note, position, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000201', 'Cash box', 'CASH', NULL, 1, now(), now()),
    ('a0000000-0000-4000-8000-000000000202', 'bKash', 'BKASH', NULL, 2, now(), now()),
    ('a0000000-0000-4000-8000-000000000203', 'Nagad', 'NAGAD', NULL, 3, now(), now()),
    ('a0000000-0000-4000-8000-000000000204', 'Rocket', 'ROCKET', NULL, 4, now(), now()),
    ('a0000000-0000-4000-8000-000000000205', 'Bank', 'BANK', NULL, 5, now(), now());

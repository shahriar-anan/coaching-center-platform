-- Accounting, part 2: moves the instructor payments already recorded into the ledger (docs/accounting/accounting-plan.md, section 5.4).
-- SQL only. Every approved or voided payment becomes a charge and a payment, paired, so every balance starts at zero and the Finance
-- totals do not change. Rejected rows are not moved. The old tables are left as they are; nothing reads them after the release.
--
-- The whole block runs in the migration's transaction. Any refusal or failed check stops the migration and changes nothing.

DO $$
DECLARE
    v_group     UUID := 'a0000000-0000-4000-8000-000000000001';
    v_charge    UUID;
    v_type      UUID;
    v_account   UUID;
    v_voided    BOOLEAN;
    v_status    VARCHAR(16);
    v_count     BIGINT;
    r           RECORD;
BEGIN
    -- Step 1: refuse to run while any old payment still waits for a decision. A waiting one has no final amount to move.
    IF EXISTS (SELECT 1 FROM offline_instructor_other_payments WHERE status = 'PENDING')
       OR EXISTS (SELECT 1 FROM offline_class_payments WHERE status = 'PENDING')
       OR EXISTS (SELECT 1 FROM payment_edits WHERE kind IN ('CLASS', 'OTHER') AND status = 'PENDING') THEN
        RAISE EXCEPTION 'Accounting move stopped: some instructor payments are still waiting for a decision. Decide them first.';
    END IF;

    -- Step 2: one payee per instructor, with the same id, in the Offline instructors group (archived ones too).
    INSERT INTO acc_payees (id, group_id, name, phone, note, archived_at, created_by, created_at, updated_at)
    SELECT id, v_group, name, phone, note, archived_at, created_by, created_at, updated_at
    FROM offline_instructors;

    -- Steps 3, 4 and 5: the hand-recorded payments and the class payments, in one order (paid on, then recorded), so the numbers
    -- follow the payment dates. A charge is inserted before its payment, so it gets the lower number.
    FOR r IN
        SELECT 'OTHER'::text AS src, p.id AS old_id, p.instructor_id AS payee_id, p.batch_id, NULL::uuid AS class_id,
               p.kind AS pay_kind, p.class_count AS qty, p.reason AS descr, p.amount, p.method, p.reference, p.paid_on,
               p.note, p.status, p.recorded_by, p.recorded_at, p.corrected_at, p.voided_by, p.voided_at, p.void_reason
        FROM offline_instructor_other_payments p
        WHERE p.status IN ('APPROVED', 'VOIDED')
        UNION ALL
        SELECT 'CLASS', cp.id, cp.instructor_id, c.batch_id, c.id,
               c.kind, 1, 'Class: ' || c.subject || ' on ' || to_char(c.held_on, 'YYYY-MM-DD'), cp.amount, cp.method,
               cp.reference, cp.paid_on, cp.note, cp.status, cp.recorded_by, cp.recorded_at, cp.corrected_at, cp.voided_by,
               cp.voided_at, cp.void_reason
        FROM offline_class_payments cp
        JOIN offline_classes c ON c.id = cp.class_id
        WHERE cp.status IN ('APPROVED', 'VOIDED')
        ORDER BY paid_on, recorded_at, old_id
    LOOP
        v_voided := r.status = 'VOIDED';
        v_status := CASE WHEN v_voided THEN 'VOIDED' ELSE 'ACTIVE' END;

        -- The type follows the kind: lecture, exam or other. Method maps to the money account.
        v_type := CASE r.pay_kind
            WHEN 'LECTURE' THEN 'a0000000-0000-4000-8000-000000000111'::uuid
            WHEN 'EXAM' THEN 'a0000000-0000-4000-8000-000000000112'::uuid
            ELSE 'a0000000-0000-4000-8000-000000000117'::uuid
        END;
        v_account := CASE r.method
            WHEN 'CASH' THEN 'a0000000-0000-4000-8000-000000000201'::uuid
            WHEN 'BKASH' THEN 'a0000000-0000-4000-8000-000000000202'::uuid
            WHEN 'NAGAD' THEN 'a0000000-0000-4000-8000-000000000203'::uuid
            WHEN 'ROCKET' THEN 'a0000000-0000-4000-8000-000000000204'::uuid
            WHEN 'BANK' THEN 'a0000000-0000-4000-8000-000000000205'::uuid
            ELSE NULL
        END;

        -- The charge: what the payee was owed for it. Quantity is the class count (1 for a class payment); no unit price, as the
        -- old pages typed the amount.
        v_charge := gen_random_uuid();
        INSERT INTO acc_entries (id, kind, status, group_id, payee_id, type_id, entry_date, quantity, amount, batch_id,
            description, note, legacy_source, legacy_class_id, recorded_by, recorded_at, voided_by, voided_at, void_reason,
            created_at, updated_at)
        VALUES (v_charge, 'CHARGE', v_status, v_group, r.payee_id, v_type, r.paid_on, r.qty, r.amount, r.batch_id,
            r.descr, 'Moved from instructor payments', r.src, r.class_id, r.recorded_by, r.recorded_at,
            CASE WHEN v_voided THEN r.voided_by END, CASE WHEN v_voided THEN r.voided_at END,
            CASE WHEN v_voided THEN COALESCE(r.void_reason, 'Voided before the move') END,
            r.recorded_at, r.recorded_at);

        -- The payment: the money that went out, from its money account. It points at its charge. Only the payment keeps the old row's id.
        INSERT INTO acc_entries (id, kind, status, group_id, payee_id, entry_date, amount, account_id, reference,
            batch_id, note, paired_entry_id, legacy_source, legacy_id, legacy_class_id, recorded_by, recorded_at,
            corrected_at, voided_by, voided_at, void_reason, created_at, updated_at)
        VALUES (gen_random_uuid(), 'PAYMENT', v_status, v_group, r.payee_id, r.paid_on, r.amount, v_account, r.reference,
            r.batch_id, r.note, v_charge, r.src, r.old_id, r.class_id, r.recorded_by, r.recorded_at,
            r.corrected_at, CASE WHEN v_voided THEN r.voided_by END, CASE WHEN v_voided THEN r.voided_at END,
            CASE WHEN v_voided THEN COALESCE(r.void_reason, 'Voided before the move') END,
            r.recorded_at, r.recorded_at);
    END LOOP;

    -- Step 6: checks before the migration is allowed to finish. Any mismatch stops it.
    IF (SELECT COALESCE(SUM(amount), 0) FROM acc_entries WHERE group_id = v_group AND kind = 'PAYMENT' AND status = 'ACTIVE')
       <> (SELECT COALESCE(SUM(amount), 0) FROM offline_instructor_other_payments WHERE status = 'APPROVED')
          + (SELECT COALESCE(SUM(amount), 0) FROM offline_class_payments WHERE status = 'APPROVED') THEN
        RAISE EXCEPTION 'Accounting move stopped: the moved payments do not add up to the old approved payments.';
    END IF;

    IF EXISTS (SELECT 1 FROM acc_entries WHERE group_id = v_group AND status = 'ACTIVE' AND payee_id IS NOT NULL
               GROUP BY payee_id
               HAVING SUM(CASE kind WHEN 'CHARGE' THEN amount WHEN 'PAYMENT' THEN -amount ELSE 0 END) <> 0) THEN
        RAISE EXCEPTION 'Accounting move stopped: a moved payee does not start with a zero balance.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM acc_entries WHERE kind = 'PAYMENT' AND legacy_source = 'OTHER';
    IF v_count <> (SELECT COUNT(*) FROM offline_instructor_other_payments WHERE status IN ('APPROVED', 'VOIDED')) THEN
        RAISE EXCEPTION 'Accounting move stopped: the hand-recorded payments moved do not match the old rows.';
    END IF;

    SELECT COUNT(*) INTO v_count FROM acc_entries WHERE kind = 'PAYMENT' AND legacy_source = 'CLASS';
    IF v_count <> (SELECT COUNT(*) FROM offline_class_payments WHERE status IN ('APPROVED', 'VOIDED')) THEN
        RAISE EXCEPTION 'Accounting move stopped: the class payments moved do not match the old rows.';
    END IF;
END $$;

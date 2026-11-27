# Progress F1: schema and seed (V107_1)

Built: `api/src/main/resources/db/migration/V107_1__accounting.sql` (sequence `acc_entry_no_seq`; tables `acc_groups`, `acc_types`,
`acc_payees`, `acc_accounts`, `acc_entries`, `acc_entry_edits`; checks and indexes of plan 5.2; seed of plan 5.3 with the fixed ids).
Test: `api/src/test/java/com/coachingcenter/api/accounting/AccSchemaIT.java`.

Tests (acc-mvn.ps1 -Agent F -Tests "AccSchemaIT"): 20 run, 0 failures, 0 errors.
Covers the seed (ids, counts, names), each check of plan 5.2 with its own refused row, the partial unique indexes (a name or a
live user is reused after archive), the sequence default for `no`, and a required edit reason.

Decisions taken where the plan was silent (the safer reading):
- `acc_payees` has one more partial unique index: a site user is linked to at most one live payee (plan 4.3 says so). The service
  checks it too.
- `acc_entry_edits` has `created_at` and `updated_at` as well as `edited_at`, because plan 5.2 says every table has them.
- `acc_entries.no` defaults to `nextval('acc_entry_no_seq')`, so tests and the move need not pass it.
- Seeded type names: Bangla names are given for every type and group (natural terms from the site: ক্লাস, খাতা দেখা, কর্তন, ...).
- The Bangla "Overtime" is ওভারটাইম with unit ঘণ্টা; "Festival bonus" is উৎসব বোনাস.

Open questions for the orchestrator:
- `acc_accounts` has no Bangla column (plan 5.2 lists `name`, `kind`, `note` only), so the five money accounts are seeded with English
  names: Cash box, bKash, Nagad, Rocket, Bank. The plan's section 5.3 asks for Bangla on every seeded row. If the web should show
  Bangla for accounts, it can map by `kind`, or a `name_bn` column can be added before the release (a question, not changed here).

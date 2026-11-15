-- Instalments set one by one (instalment-plan-and-early-payments.md, 15 Nov): the admin's own rows for an admission before onboarding,
-- as [{"dueOn": "yyyy-MM-dd", "amount": "4000.00"}, ...]. course_fee stays their sum, installments their count and first_due_on the
-- first date. Null means the even split of course_fee over installments, as every admission set up before this change has.
-- Onboarding writes the dues from it. The previous release ignores the column.
ALTER TABLE offline_admissions ADD COLUMN installment_plan JSONB NULL;

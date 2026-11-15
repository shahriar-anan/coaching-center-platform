-- The code a student needs after onboarding, shown in the app as well as by email (15 Nov).
--
-- student_notices.code: the join code in the student's own join-code notice. A join code only works for the account it was sent to,
-- and only that account sees the notice. It is cleared from older notices when a new code is sent, when the student joins, and when
-- the code stops working (sent back, back to review, closed). Every other notice leaves it empty.
--
-- offline_admissions.sent_code: the last code sent for the admission (a join code, or the activation code of an account the onboarding
-- made), so staff can read it out to the student. Staff screens show it only while it can still be used. It is cleared with the code.
ALTER TABLE student_notices ADD COLUMN code VARCHAR(16) NULL;
ALTER TABLE offline_admissions ADD COLUMN sent_code VARCHAR(16) NULL;

-- The course fee is the total a student pays for a batch, paid in the number of monthly instalments the admin chooses
-- (course-fee-plan.md, 15 Nov). Until now the batch and each admission held a monthly amount that was repeated for every month
-- to the course end.
--
-- The new columns hold the total and the instalment count. The old monthly columns stay and are kept filled with the first
-- instalment's amount, so the previous release still reads a sensible value if it has to be put back.

ALTER TABLE offline_batches ADD COLUMN course_fee NUMERIC(12, 2);
ALTER TABLE offline_batches ADD COLUMN default_installments SMALLINT;

ALTER TABLE offline_admissions ADD COLUMN course_fee NUMERIC(12, 2);
ALTER TABLE offline_admissions ADD COLUMN installments SMALLINT;

-- The month counts below are the number of monthly dates from a first date up to an end date: the same day each month, clamped to
-- the month's end, as the schedule counts them; at least 1 and at most 24.

-- Batches: the amount entered so far becomes the course fee (decision C4), in as many instalments as the batch has months.
UPDATE offline_batches
SET course_fee = default_monthly_fee,
    default_installments = GREATEST(1, LEAST(24, (
      SELECT count(*) FROM generate_series(0, 240) AS k
      WHERE (COALESCE(starts_on, created_at::date) + make_interval(months => k))::date <= ends_on
    )));

-- Admissions with a fee schedule keep exactly what they owe: the total and the number of their live instalments.
UPDATE offline_admissions a
SET course_fee = d.total,
    installments = LEAST(120, d.n)
FROM (
  SELECT admission_id, sum(amount) AS total, count(*) AS n
  FROM offline_fee_dues
  WHERE cancelled_at IS NULL
  GROUP BY admission_id
) d
WHERE d.admission_id = a.id;

-- Admissions with no schedule yet (not onboarded): the amount entered becomes the course fee (decision C4), in as many
-- instalments as fit from the first due date to the course end.
UPDATE offline_admissions
SET course_fee = monthly_fee,
    installments = CASE
      WHEN first_due_on IS NOT NULL AND course_ends_on IS NOT NULL
        THEN GREATEST(1, LEAST(24, (
          SELECT count(*) FROM generate_series(0, 240) AS k
          WHERE (first_due_on + make_interval(months => k))::date <= course_ends_on
        )))
      ELSE NULL
    END
WHERE course_fee IS NULL AND monthly_fee IS NOT NULL;

-- The old monthly columns now hold the first instalment (whole taka), as the new release keeps them, so the previous release reads
-- a per-month amount close to the truth if it is ever put back. An admission with instalments keeps its own first amount.
UPDATE offline_batches
SET default_monthly_fee = floor(course_fee / default_installments);

UPDATE offline_admissions a
SET monthly_fee = floor(a.course_fee / a.installments)
WHERE a.course_fee IS NOT NULL AND a.installments IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM offline_fee_dues d WHERE d.admission_id = a.id AND d.cancelled_at IS NULL);

ALTER TABLE offline_batches ALTER COLUMN course_fee SET NOT NULL;
ALTER TABLE offline_batches ALTER COLUMN default_installments SET NOT NULL;
ALTER TABLE offline_batches ADD CONSTRAINT offline_batches_course_fee_check CHECK (course_fee >= 0);
ALTER TABLE offline_batches ADD CONSTRAINT offline_batches_default_installments_check CHECK (default_installments BETWEEN 1 AND 24);
ALTER TABLE offline_admissions ADD CONSTRAINT offline_admissions_course_fee_check CHECK (course_fee IS NULL OR course_fee >= 0);
ALTER TABLE offline_admissions ADD CONSTRAINT offline_admissions_installments_check
  CHECK (installments IS NULL OR installments BETWEEN 1 AND 120);

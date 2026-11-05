-- Instructor and pay per written exam (staff pay plan, phase 8, decision P18). New tables only: no existing table or row is
-- changed, and no existing row is backfilled. A written exam with no row here earns at the instructor's rate or the default.

-- The exam's own pay per sheet, set by the Master Admin. One row per exam that has its own rate.
CREATE TABLE live_exam_pay_terms (
    exam_id      UUID PRIMARY KEY REFERENCES live_exams (id),
    sheet_rate   NUMERIC(12,2) NOT NULL,
    updated_by   UUID NULL REFERENCES users (id),
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_pay_terms_sheet_rate_check CHECK (sheet_rate > 0)
);

-- Append only: one row per change to an exam's marker or pay per sheet. The exam's history.
--   ASSIGNED      instructor_id = the instructor now; previous_instructor_id = the instructor before this row (or null).
--   UNASSIGNED    instructor_id = null (nobody now); previous_instructor_id = the instructor removed.
--   RATE_SET      sheet_rate = the new rate; previous_sheet_rate = null.
--   RATE_CHANGED  sheet_rate = the new rate; previous_sheet_rate = the rate before.
-- changed_by is the actor when it is known, else null.
CREATE TABLE live_exam_assignment_log (
    id                      UUID PRIMARY KEY,
    exam_id                 UUID NOT NULL REFERENCES live_exams (id),
    kind                    VARCHAR(24) NOT NULL,
    instructor_id           UUID NULL REFERENCES users (id),
    previous_instructor_id  UUID NULL REFERENCES users (id),
    sheet_rate              NUMERIC(12,2) NULL,
    previous_sheet_rate     NUMERIC(12,2) NULL,
    changed_by              UUID NULL REFERENCES users (id),
    created_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_assignment_log_kind_check CHECK (kind IN ('ASSIGNED', 'UNASSIGNED', 'RATE_SET', 'RATE_CHANGED'))
);

CREATE INDEX live_exam_assignment_log_exam_created_idx ON live_exam_assignment_log (exam_id, created_at);

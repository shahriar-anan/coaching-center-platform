-- Offline admissions: one row per student admitted in person, from the first form to the close (plan docs/offline-admission).
-- The form, the status and the batch are kept on the admission. Each submitted form is kept as a row of its own.

CREATE TABLE offline_admissions (
    id                        UUID PRIMARY KEY,
    student_id                UUID NOT NULL REFERENCES users (id),
    status                    VARCHAR(24) NOT NULL,
    created_by                UUID NOT NULL REFERENCES users (id),
    created_at                TIMESTAMPTZ NOT NULL,
    version                   BIGINT NOT NULL DEFAULT 0,
    full_name                 VARCHAR(200) NULL,
    father_name               VARCHAR(200) NULL,
    mother_name               VARCHAR(200) NULL,
    date_of_birth             DATE NULL,
    email                     VARCHAR(255) NULL,
    gender                    VARCHAR(16) NULL,
    university                VARCHAR(200) NULL,
    semester_batch            VARCHAR(100) NULL,
    nid                       VARCHAR(32) NULL,
    mobile                    VARCHAR(32) NULL,
    emergency_mobile          VARCHAR(32) NULL,
    hall_mess                 VARCHAR(300) NULL,
    thana                     VARCHAR(100) NULL,
    district                  VARCHAR(100) NULL,
    present_address           VARCHAR(1000) NULL,
    permanent_address         VARCHAR(1000) NULL,
    photo_stored_name         VARCHAR(255) NULL,
    photo_content_type        VARCHAR(100) NULL,
    photo_byte_size           BIGINT NULL,
    draft_saved_at            TIMESTAMPTZ NULL,
    submitted_at              TIMESTAMPTZ NULL,
    submit_count              INTEGER NOT NULL DEFAULT 0,
    changes_reason            VARCHAR(500) NULL,
    changes_requested_by      UUID NULL REFERENCES users (id),
    changes_requested_at      TIMESTAMPTZ NULL,
    batch_id                  UUID NULL REFERENCES offline_batches (id),
    monthly_fee               NUMERIC(12,2) NULL,
    admitted_on               DATE NULL,
    first_due_on              DATE NULL,
    course_ends_on            DATE NULL,
    ready_by                  UUID NULL REFERENCES users (id),
    ready_at                  TIMESTAMPTZ NULL,
    onboarded_by              UUID NULL REFERENCES users (id),
    onboarded_at              TIMESTAMPTZ NULL,
    signed_stored_name        VARCHAR(255) NULL,
    signed_original_name      VARCHAR(500) NULL,
    signed_content_type       VARCHAR(100) NULL,
    signed_byte_size          BIGINT NULL,
    signed_uploaded_by        UUID NULL REFERENCES users (id),
    signed_uploaded_at        TIMESTAMPTZ NULL,
    closed_at                 TIMESTAMPTZ NULL,
    closed_reason             VARCHAR(500) NULL,
    CONSTRAINT offline_admissions_status_check CHECK (
        status IN ('AWAITING_FORM', 'SUBMITTED', 'CHANGES_REQUESTED', 'READY', 'ONBOARDED', 'CLOSED'))
);

-- A student has at most one open admission at a time. A closed one does not count, so a student can be admitted again.
CREATE UNIQUE INDEX offline_admissions_one_open_per_student
    ON offline_admissions (student_id) WHERE status <> 'CLOSED';

-- One row per form the student sent in. The form is kept as the JSON text the student sent, so a later edit never rewrites it.
CREATE TABLE offline_admission_submissions (
    id              UUID PRIMARY KEY,
    admission_id    UUID NOT NULL REFERENCES offline_admissions (id),
    submitted_at    TIMESTAMPTZ NOT NULL,
    submitted_by    UUID NOT NULL REFERENCES users (id),
    form_json       TEXT NOT NULL,
    returned_reason VARCHAR(500) NULL,
    returned_by     UUID NULL REFERENCES users (id),
    returned_at     TIMESTAMPTZ NULL
);

-- The exams or packages an admission is given access to. A removed item stays in the table as history.
CREATE TABLE offline_admission_items (
    id            UUID PRIMARY KEY,
    admission_id  UUID NOT NULL REFERENCES offline_admissions (id),
    scope         VARCHAR(8) NOT NULL,
    exam_id       UUID NULL REFERENCES live_exams (id),
    module_id     UUID NULL REFERENCES live_exam_modules (id),
    grant_id      UUID NULL REFERENCES live_exam_grants (id),
    added_by      UUID NOT NULL REFERENCES users (id),
    added_at      TIMESTAMPTZ NOT NULL,
    removed_at    TIMESTAMPTZ NULL,
    CONSTRAINT offline_admission_items_scope_check CHECK (scope IN ('EXAM', 'MODULE')),
    CONSTRAINT offline_admission_items_target_check CHECK (
        (scope = 'EXAM' AND exam_id IS NOT NULL AND module_id IS NULL)
        OR (scope = 'MODULE' AND module_id IS NOT NULL AND exam_id IS NULL))
);

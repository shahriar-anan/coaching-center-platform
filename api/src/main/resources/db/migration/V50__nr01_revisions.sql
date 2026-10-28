-- NR-01: staged edits. A published exam or package keeps its live version until the Master Admin publishes the staged one.

CREATE TABLE live_exam_revisions (
    id          UUID PRIMARY KEY,
    exam_id     UUID NOT NULL UNIQUE REFERENCES live_exams (id),
    payload     JSONB NOT NULL,
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE live_exam_module_revisions (
    id          UUID PRIMARY KEY,
    module_id   UUID NOT NULL UNIQUE REFERENCES live_exam_modules (id),
    payload     JSONB NOT NULL,
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_by  UUID NOT NULL REFERENCES users (id),
    updated_at  TIMESTAMPTZ NOT NULL
);

-- The change list that was published, one row per publish. rescored counts the papers re-scored by that publish.
CREATE TABLE live_exam_change_log (
    id            UUID PRIMARY KEY,
    exam_id       UUID NULL REFERENCES live_exams (id),
    module_id     UUID NULL REFERENCES live_exam_modules (id),
    changes       JSONB NOT NULL,
    rescored      INTEGER NOT NULL DEFAULT 0,
    published_by  UUID NOT NULL REFERENCES users (id),
    published_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_change_log_one_target_check CHECK (num_nonnulls(exam_id, module_id) = 1)
);

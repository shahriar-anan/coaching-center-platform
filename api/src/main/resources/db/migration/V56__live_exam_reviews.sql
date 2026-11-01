-- Exam and package reviews (plan section 5). A student rates an exam or a package 1 to 5 and may write a comment.
-- One review per student per target. The comment is checked for length in the service (at most 1000 characters).
CREATE TABLE live_exam_reviews (
    id           UUID PRIMARY KEY,
    exam_id      UUID NULL REFERENCES live_exams (id),
    module_id    UUID NULL REFERENCES live_exam_modules (id),
    student_id   UUID NOT NULL REFERENCES users (id),
    rating       SMALLINT NOT NULL,
    comment      TEXT NULL,
    status       VARCHAR(16) NOT NULL,
    hide_reason  VARCHAR(300) NULL,
    featured     BOOLEAN NOT NULL DEFAULT false,
    moderated_by UUID NULL,
    moderated_at TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT live_exam_reviews_one_target_check CHECK (num_nonnulls(exam_id, module_id) = 1),
    CONSTRAINT live_exam_reviews_rating_check CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT live_exam_reviews_status_check CHECK (status IN ('PENDING', 'APPROVED', 'HIDDEN')),
    CONSTRAINT live_exam_reviews_featured_check CHECK (
        NOT featured OR (status = 'APPROVED' AND comment IS NOT NULL)
    )
);

CREATE UNIQUE INDEX live_exam_reviews_exam_student_unique ON live_exam_reviews (exam_id, student_id)
    WHERE exam_id IS NOT NULL;
CREATE UNIQUE INDEX live_exam_reviews_module_student_unique ON live_exam_reviews (module_id, student_id)
    WHERE module_id IS NOT NULL;

CREATE INDEX live_exam_reviews_exam_status_idx ON live_exam_reviews (exam_id, status);
CREATE INDEX live_exam_reviews_module_status_idx ON live_exam_reviews (module_id, status);

CREATE INDEX live_exam_reviews_featured_idx ON live_exam_reviews (moderated_at DESC)
    WHERE featured;

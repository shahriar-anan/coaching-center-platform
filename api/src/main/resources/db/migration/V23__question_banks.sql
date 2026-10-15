CREATE TABLE question_banks (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE TABLE question_bank_items (
    id                  UUID PRIMARY KEY,
    bank_id             UUID NOT NULL REFERENCES question_banks (id),
    question_id         UUID NOT NULL REFERENCES questions (id),
    position            INTEGER NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX question_bank_items_bank_question_idx ON question_bank_items (bank_id, question_id)
    WHERE deleted_at IS NULL;

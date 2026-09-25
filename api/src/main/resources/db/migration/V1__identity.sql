CREATE TABLE users (
    id                  UUID PRIMARY KEY,
    phone               VARCHAR(16) NOT NULL,
    email               VARCHAR(320) NOT NULL,
    email_verified_at   TIMESTAMPTZ NULL,
    password_hash       VARCHAR(255) NULL,
    role                VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    last_login_at       TIMESTAMPTZ NULL,
    created_by          UUID NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    deleted_at          TIMESTAMPTZ NULL,
    CONSTRAINT users_phone_format CHECK (phone ~ '^\+8801[0-9]{9}$'),
    CONSTRAINT users_role_check CHECK (role IN ('STUDENT', 'MASTER_ADMIN', 'SYSTEM_ADMIN')),
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX users_phone_active_idx ON users (phone) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX users_email_active_idx ON users (email) WHERE deleted_at IS NULL;

CREATE TABLE student_profiles (
    user_id         UUID PRIMARY KEY REFERENCES users (id),
    full_name       VARCHAR(255) NOT NULL,
    student_code    VARCHAR(64) NOT NULL,
    CONSTRAINT student_profiles_student_code_key UNIQUE (student_code)
);

CREATE TABLE admin_profiles (
    user_id     UUID PRIMARY KEY REFERENCES users (id),
    full_name   VARCHAR(255) NOT NULL
);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id),
    token_hash      VARCHAR(255) NOT NULL,
    family_id       UUID NOT NULL,
    client_type     VARCHAR(16) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ NULL,
    replaced_by     UUID NULL REFERENCES refresh_tokens (id),
    created_at      TIMESTAMPTZ NOT NULL,
    ip              VARCHAR(64) NULL,
    user_agent      VARCHAR(1024) NULL,
    CONSTRAINT refresh_tokens_client_type_check CHECK (client_type IN ('WEB', 'MOBILE'))
);

CREATE TABLE one_time_codes (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users (id),
    purpose             VARCHAR(32) NOT NULL,
    code_hash           VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ NULL,
    failed_attempts     INT NOT NULL DEFAULT 0,
    created_by          UUID NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT one_time_codes_purpose_check CHECK (purpose IN ('ACTIVATION', 'PASSWORD_RESET', 'EMAIL_VERIFY'))
);

CREATE TABLE audit_log (
    id              UUID PRIMARY KEY,
    actor_user_id   UUID NULL,
    action          VARCHAR(64) NOT NULL,
    entity_type     VARCHAR(64) NOT NULL,
    entity_id       UUID NULL,
    metadata        JSONB NULL,
    ip              VARCHAR(64) NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE FUNCTION audit_log_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;

CREATE TRIGGER audit_log_no_update
    BEFORE UPDATE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_append_only();

CREATE TRIGGER audit_log_no_delete
    BEFORE DELETE ON audit_log
    FOR EACH ROW
    EXECUTE FUNCTION audit_log_append_only();

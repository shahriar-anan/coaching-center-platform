ALTER TABLE users DROP CONSTRAINT users_phone_format;
ALTER TABLE users ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE users
    ADD CONSTRAINT users_phone_format CHECK (phone IS NULL OR phone ~ '^\+8801[0-9]{9}$');

ALTER TABLE users ADD COLUMN google_sub VARCHAR(255) NULL;
CREATE UNIQUE INDEX users_google_sub_active_idx
    ON users (google_sub) WHERE google_sub IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE google_oauth_states (
    state_hash  VARCHAR(64) PRIMARY KEY,
    expires_at  TIMESTAMPTZ NOT NULL
);

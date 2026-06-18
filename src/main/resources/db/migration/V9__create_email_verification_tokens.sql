CREATE TABLE email_verification_tokens
(
    id                     UUID PRIMARY KEY,
    user_id                UUID         NOT NULL,
    email                  VARCHAR(320) NOT NULL,
    verification_code_hash VARCHAR(255) NOT NULL,
    encrypted_code         TEXT         NOT NULL,
    expires_at             TIMESTAMPTZ  NOT NULL,
    consumed_at            TIMESTAMPTZ,
    revoked_at             TIMESTAMPTZ,
    failed_attempts        INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at        TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_email_verification_tokens_id_user_id UNIQUE (id, user_id),

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    CONSTRAINT chk_email_verification_tokens_failed_attempts
        CHECK (failed_attempts >= 0),

    CONSTRAINT chk_email_verification_tokens_updated_at
        CHECK (updated_at >= created_at),

    CONSTRAINT chk_email_verification_tokens_expires_at
        CHECK (expires_at > created_at),

    CONSTRAINT chk_email_verification_tokens_consumed_at
        CHECK (consumed_at IS NULL OR consumed_at >= created_at),

    CONSTRAINT chk_email_verification_tokens_revoked_at
        CHECK (revoked_at IS NULL OR revoked_at >= created_at),

    CONSTRAINT chk_email_verification_tokens_last_attempt_at
        CHECK (last_attempt_at IS NULL OR last_attempt_at >= created_at),

    CONSTRAINT chk_email_verification_tokens_not_consumed_and_revoked
        CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_email_verification_tokens_user_id
    ON email_verification_tokens (user_id);

CREATE INDEX idx_email_verification_tokens_email
    ON email_verification_tokens (email);

CREATE INDEX idx_email_verification_tokens_user_email
    ON email_verification_tokens (user_id, email);

CREATE INDEX idx_email_verification_tokens_expires_at
    ON email_verification_tokens (expires_at);

CREATE INDEX idx_email_verification_tokens_open_lookup
    ON email_verification_tokens (user_id, email, created_at DESC)
    WHERE consumed_at IS NULL
        AND revoked_at IS NULL;

CREATE UNIQUE INDEX uk_email_verification_tokens_open_user_email
    ON email_verification_tokens (user_id, email)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

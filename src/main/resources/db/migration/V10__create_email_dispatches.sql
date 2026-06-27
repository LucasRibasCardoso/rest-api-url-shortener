CREATE TABLE email_dispatches
(
    id                    UUID PRIMARY KEY,
    event_id              UUID         NOT NULL,
    user_id               UUID         NOT NULL,
    verification_token_id UUID         NOT NULL,
    email                 VARCHAR(320) NOT NULL,
    purpose               VARCHAR(50)  NOT NULL,
    reason                VARCHAR(50)  NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    provider_message_id   VARCHAR(255),
    send_attempts         INTEGER      NOT NULL DEFAULT 0,
    sending_started_at    TIMESTAMPTZ,
    accepted_at           TIMESTAMPTZ,
    failed_at             TIMESTAMPTZ,
    last_error_code       VARCHAR(100),
    last_error_message    TEXT,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_email_dispatches_event_id UNIQUE (event_id),

    CONSTRAINT fk_email_dispatches_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_email_dispatches_verification_token
        FOREIGN KEY (verification_token_id, user_id) REFERENCES email_verification_tokens (id, user_id),

    CONSTRAINT chk_email_dispatches_purpose CHECK (purpose IN ('EMAIL_VERIFICATION')),
    CONSTRAINT chk_email_dispatches_reason CHECK (reason IN ('REGISTER', 'RESEND')),
    CONSTRAINT chk_email_dispatches_status CHECK (status IN ('PENDING', 'SENDING', 'ACCEPTED', 'FAILED')),
    CONSTRAINT chk_email_dispatches_send_attempts CHECK (send_attempts >= 0),
    CONSTRAINT chk_email_dispatches_updated_at CHECK (updated_at >= created_at),
    CONSTRAINT chk_email_dispatches_sending_started_at CHECK (sending_started_at IS NULL OR sending_started_at >= created_at),
    CONSTRAINT chk_email_dispatches_accepted_at CHECK (accepted_at IS NULL OR accepted_at >= created_at),
    CONSTRAINT chk_email_dispatches_failed_at CHECK (failed_at IS NULL OR failed_at >= created_at)
);

CREATE INDEX idx_email_dispatches_user_id ON email_dispatches (user_id);
CREATE INDEX idx_email_dispatches_verification_token_id ON email_dispatches (verification_token_id);
CREATE INDEX idx_email_dispatches_status ON email_dispatches (status);
CREATE INDEX idx_email_dispatches_user_email ON email_dispatches (user_id, email);
CREATE INDEX idx_email_dispatches_status_sending_started_at ON email_dispatches (status, sending_started_at);

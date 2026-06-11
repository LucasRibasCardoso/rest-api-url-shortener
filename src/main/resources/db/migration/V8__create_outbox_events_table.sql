CREATE TABLE outbox_events
(
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(80)  NOT NULL,
    aggregate_id    VARCHAR(160) NOT NULL,
    event_type      VARCHAR(120) NOT NULL,
    schema_version  INTEGER      NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(30)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ,
    CONSTRAINT chk_outbox_events_schema_version CHECK (schema_version > 0),
    CONSTRAINT chk_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_events_last_error_length CHECK (
        last_error IS NULL OR char_length(last_error) <= 2000
        ),
    CONSTRAINT chk_outbox_events_status CHECK (
        status IN ('PENDING', 'PUBLISHED', 'FAILED')
        ),
    CONSTRAINT chk_outbox_events_state CHECK (
        (
            status = 'PENDING'
                AND published_at IS NULL
                AND (
                (attempts = 0 AND last_error IS NULL AND next_attempt_at IS NULL)
                    OR (attempts > 0 AND last_error IS NOT NULL AND next_attempt_at IS NOT NULL)
                )
            )
            OR (
            status = 'PUBLISHED'
                AND published_at IS NOT NULL
                AND published_at >= created_at
                AND last_error IS NULL
                AND next_attempt_at IS NULL
            )
            OR (
            status = 'FAILED'
                AND attempts > 0
                AND last_error IS NOT NULL
                AND published_at IS NULL
                AND next_attempt_at IS NULL
            )
        )
);

CREATE INDEX idx_outbox_events_status_next_attempt_at ON outbox_events (status, next_attempt_at);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_events_event_type ON outbox_events (event_type);

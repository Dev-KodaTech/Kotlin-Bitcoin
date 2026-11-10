CREATE TABLE outbox_events (
    id           UUID        PRIMARY KEY,
    type         VARCHAR(32) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    sent_at      TIMESTAMPTZ NULL
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

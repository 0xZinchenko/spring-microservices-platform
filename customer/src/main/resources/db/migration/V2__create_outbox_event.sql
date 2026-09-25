CREATE TABLE outbox_event
(
    id           BIGSERIAL PRIMARY KEY,
    exchange     VARCHAR(255)  NOT NULL,
    routing_key  VARCHAR(255)  NOT NULL,
    payload_type VARCHAR(255)  NOT NULL,
    payload      TEXT          NOT NULL,
    created_at   TIMESTAMP(6)  NOT NULL,
    published_at TIMESTAMP(6),
    attempts     INTEGER       NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000)
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event (id) WHERE published_at IS NULL;

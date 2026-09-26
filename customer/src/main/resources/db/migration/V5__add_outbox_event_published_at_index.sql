CREATE INDEX idx_outbox_event_published_at ON outbox_event (published_at) WHERE published_at IS NOT NULL;

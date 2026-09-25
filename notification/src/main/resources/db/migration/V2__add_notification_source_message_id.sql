ALTER TABLE notification ADD COLUMN source_message_id VARCHAR(255);

CREATE UNIQUE INDEX uq_notification_source_message_id ON notification (source_message_id);

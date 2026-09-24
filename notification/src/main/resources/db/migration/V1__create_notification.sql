CREATE SEQUENCE notification_id_sequence START WITH 1 INCREMENT BY 50;

CREATE TABLE notification
(
    notification_id   INTEGER PRIMARY KEY,
    to_customer_id    INTEGER,
    to_customer_email VARCHAR(255),
    sender            VARCHAR(255),
    message           VARCHAR(255),
    sent_at           TIMESTAMP(6)
);

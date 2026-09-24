CREATE SEQUENCE fraud_id_sequence START WITH 1 INCREMENT BY 50;

CREATE TABLE fraud_check_history
(
    id           INTEGER PRIMARY KEY,
    customer_id  INTEGER,
    is_fraudster BOOLEAN,
    created_at   TIMESTAMP(6)
);

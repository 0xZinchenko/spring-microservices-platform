CREATE SEQUENCE customer_id_sequence START WITH 1 INCREMENT BY 50;

CREATE TABLE customer
(
    id         INTEGER PRIMARY KEY,
    first_name VARCHAR(255),
    last_name  VARCHAR(255),
    email      VARCHAR(255)
);

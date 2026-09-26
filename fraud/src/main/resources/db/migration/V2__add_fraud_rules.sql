ALTER TABLE fraud_check_history ADD COLUMN reason VARCHAR(50);

CREATE TABLE blocked_email
(
    email      VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

CREATE TABLE blocked_email_domain
(
    domain     VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL DEFAULT now()
);

INSERT INTO blocked_email_domain (domain)
VALUES ('mailinator.com'),
       ('yopmail.com'),
       ('guerrillamail.com'),
       ('10minutemail.com'),
       ('temp-mail.org'),
       ('trashmail.com');

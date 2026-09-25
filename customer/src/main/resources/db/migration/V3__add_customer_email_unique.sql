UPDATE customer SET email = lower(trim(email));

ALTER TABLE customer ADD CONSTRAINT uq_customer_email UNIQUE (email);

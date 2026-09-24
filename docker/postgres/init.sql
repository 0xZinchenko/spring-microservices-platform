-- Creates a separate database for each microservice.
-- Runs only on the first start, when the postgres volume is empty.
CREATE DATABASE customer;
CREATE DATABASE fraud;
CREATE DATABASE notification;

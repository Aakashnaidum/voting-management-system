-- Synthetic demonstration data. All names, e-mails and phone numbers are invented.
-- Accounts are created by DemoData.java so that passwords are hashed at runtime;
-- this file only holds reference data.

INSERT INTO constituencies (district, name) VALUES ('Chennai', 'Anna Nagar');
INSERT INTO constituencies (district, name) VALUES ('Chennai', 'Mylapore');
INSERT INTO constituencies (district, name) VALUES ('Madurai', 'Madurai Central');

INSERT INTO elections (name, status) VALUES ('Demo Assembly Election', 'OPEN');

ALTER TABLE bookings DROP CONSTRAINT bookings_status_check;
ALTER TABLE bookings ADD CHECK (status IN ('OPEN', 'PENDING_PAYMENT', 'CANCELLED')),
    ADD COLUMN payment_method varchar(20) CHECK (payment_method IN ('PIX', 'CREDIT_CARD', 'DEBIT_CARD')),
    ADD COLUMN payment_due numeric(10,2) CHECK (payment_due > 0),
    ADD COLUMN payment_amount numeric(10,2) CHECK (payment_amount > 0);

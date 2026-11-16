--liquibase formatted sql

--changeset school-fee:045-transfer-request-payment-request
--comment A counter transfer for exactly what an e-mailed request asked for carries the e-mail's own order code, so the parent's QR and the cashier's QR are the same. This column names that e-mail request.

ALTER TABLE fee_transfer_requests ADD COLUMN payment_request_id BIGINT REFERENCES fee_payment_requests (id);
CREATE INDEX ix_fee_transfer_requests_payment_request ON fee_transfer_requests (payment_request_id);

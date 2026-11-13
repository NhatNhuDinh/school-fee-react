--liquibase formatted sql

--changeset school-fee:044-create-fee-transfer-requests
--comment A transfer asked for at the counter: one order code, one amount, a set of receivables. It is paid when the bank reports the money (webhook or a manual check) or when the cashier confirms it by hand.

CREATE TABLE fee_transfer_requests (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT         NOT NULL REFERENCES organizations (id),
    academic_year_id  BIGINT         NOT NULL REFERENCES academic_years (id),
    enrollment_id     BIGINT         NOT NULL REFERENCES student_enrollments (id),
    order_code        VARCHAR(40)    NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    expires_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by_id     BIGINT,
    created_by_name   VARCHAR(200),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    paid_at           TIMESTAMP WITH TIME ZONE,
    paid_by           VARCHAR(20),
    CONSTRAINT uq_fee_transfer_requests_order_code UNIQUE (order_code),
    CONSTRAINT ck_fee_transfer_requests_status CHECK (status IN ('PENDING', 'DEFERRED', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_fee_transfer_requests_paid_by CHECK (paid_by IS NULL OR paid_by IN ('BANK', 'MANUAL'))
);
CREATE INDEX ix_fee_transfer_requests_year_status ON fee_transfer_requests (academic_year_id, status);
CREATE INDEX ix_fee_transfer_requests_enrollment ON fee_transfer_requests (enrollment_id);

CREATE TABLE fee_transfer_request_lines (
    id            BIGSERIAL PRIMARY KEY,
    request_id    BIGINT NOT NULL REFERENCES fee_transfer_requests (id) ON DELETE CASCADE,
    receivable_id BIGINT NOT NULL REFERENCES student_fee_receivables (id),
    CONSTRAINT uq_fee_transfer_request_lines UNIQUE (request_id, receivable_id)
);

--rollback DROP TABLE fee_transfer_request_lines; DROP TABLE fee_transfer_requests;

--changeset school-fee:044-create-bank-transactions
--comment Every money-in line the bank reported, from the SePay webhook or from a manual check. Kept whether or not it matched anything: this table is the audit trail and the worklist of what needs a person.

CREATE TABLE bank_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    organization_id     BIGINT REFERENCES organizations (id),
    source              VARCHAR(20)    NOT NULL,
    sepay_id            BIGINT,
    sepay_uuid          VARCHAR(64),
    dedupe_key          VARCHAR(64)    NOT NULL,
    gateway             VARCHAR(100),
    account_number      VARCHAR(50),
    transaction_time    TIMESTAMP WITH TIME ZONE NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    transfer_type       VARCHAR(10)    NOT NULL,
    content             TEXT,
    code                VARCHAR(100),
    reference_code      VARCHAR(100),
    raw_payload         TEXT,
    status              VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    reason              VARCHAR(30),
    note                TEXT,
    transfer_request_id BIGINT REFERENCES fee_transfer_requests (id),
    payment_request_id  BIGINT REFERENCES fee_payment_requests (id),
    suggested_enrollment_id BIGINT REFERENCES student_enrollments (id),
    attempts            INTEGER        NOT NULL DEFAULT 0,
    handled_by_name     VARCHAR(200),
    handled_at          TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_bank_transactions_dedupe UNIQUE (dedupe_key),
    CONSTRAINT ck_bank_transactions_source CHECK (source IN ('WEBHOOK', 'API_CHECK')),
    CONSTRAINT ck_bank_transactions_status CHECK (status IN ('RECEIVED', 'MATCHED', 'NEEDS_REVIEW', 'IGNORED', 'RESOLVED'))
);
CREATE UNIQUE INDEX uq_bank_transactions_sepay_id ON bank_transactions (sepay_id) WHERE sepay_id IS NOT NULL;
CREATE UNIQUE INDEX uq_bank_transactions_sepay_uuid ON bank_transactions (sepay_uuid) WHERE sepay_uuid IS NOT NULL;
CREATE INDEX ix_bank_transactions_org_status ON bank_transactions (organization_id, status, transaction_time DESC);

ALTER TABLE fee_payments ADD COLUMN transfer_request_id BIGINT REFERENCES fee_transfer_requests (id);
ALTER TABLE fee_payments ADD COLUMN bank_transaction_id BIGINT REFERENCES bank_transactions (id);

--rollback ALTER TABLE fee_payments DROP COLUMN bank_transaction_id; ALTER TABLE fee_payments DROP COLUMN transfer_request_id; DROP TABLE bank_transactions;

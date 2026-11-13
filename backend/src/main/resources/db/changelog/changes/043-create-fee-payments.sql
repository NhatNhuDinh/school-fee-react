--liquibase formatted sql

--changeset school-fee:043-create-fee-payments
--comment A receipt (chứng từ thu tiền) is one student's payment into one đợt thu, taken whole: the lines are the receivables it settled, each for what was still owed.

CREATE TABLE fee_payments (
    id                BIGSERIAL PRIMARY KEY,
    organization_id   BIGINT       NOT NULL REFERENCES organizations (id),
    academic_year_id  BIGINT       NOT NULL REFERENCES academic_years (id),
    batch_id          BIGINT       NOT NULL REFERENCES fee_collection_batches (id),
    batch_student_id  BIGINT       NOT NULL REFERENCES fee_collection_batch_students (id),
    receipt_no        BIGINT       NOT NULL,
    method            VARCHAR(20)  NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL,
    paid_on           DATE         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    transfer_content  VARCHAR(60),
    collector_id      BIGINT,
    collector_name    VARCHAR(200),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    cancelled_at      TIMESTAMP WITH TIME ZONE,
    cancelled_by_name VARCHAR(200),
    CONSTRAINT uq_fee_payments_receipt UNIQUE (organization_id, receipt_no),
    CONSTRAINT ck_fee_payments_method CHECK (method IN ('CASH', 'TRANSFER')),
    CONSTRAINT ck_fee_payments_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_fee_payments_amount CHECK (amount > 0)
);
CREATE INDEX ix_fee_payments_year_date ON fee_payments (academic_year_id, paid_on DESC, id DESC);
CREATE INDEX ix_fee_payments_batch_student ON fee_payments (batch_student_id);

CREATE TABLE fee_payment_lines (
    id            BIGSERIAL PRIMARY KEY,
    payment_id    BIGINT         NOT NULL REFERENCES fee_payments (id) ON DELETE CASCADE,
    receivable_id BIGINT         NOT NULL REFERENCES student_fee_receivables (id),
    amount        NUMERIC(19, 2) NOT NULL CHECK (amount > 0)
);
CREATE INDEX ix_fee_payment_lines_payment ON fee_payment_lines (payment_id);
CREATE INDEX ix_fee_payment_lines_receivable ON fee_payment_lines (receivable_id);

--rollback DROP TABLE fee_payment_lines; DROP TABLE fee_payments;

--changeset school-fee:043-seed-fee-payment-permissions
INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('fee_payments:read',   'Xem thu tiền',    'Xem công nợ học sinh, phiếu thu và lịch sử thu tiền', TRUE),
  ('fee_payments:write',  'Thu tiền',        'Thu tiền học sinh và theo lớp, tiền mặt hoặc chuyển khoản', TRUE),
  ('fee_payments:delete', 'Hủy phiếu thu',   'Hủy phiếu thu đã lập, trả lại khoản nợ', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'fee\_payments:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'fee\_payments:%'); DELETE FROM permissions WHERE code LIKE 'fee\_payments:%';

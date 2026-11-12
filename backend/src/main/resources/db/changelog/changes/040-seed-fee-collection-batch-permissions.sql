--liquibase formatted sql

--changeset school-fee:040-seed-fee-collection-batch-permissions
--comment Permissions for fee_collection_batches. Delete is for a đợt thu nothing has been collected for.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('fee_collection_batches:read',   'Xem đợt thu',  'Xem đợt thu, khoản thu và công nợ của học sinh trong đợt', TRUE),
  ('fee_collection_batches:write',  'Sửa đợt thu',  'Lập đợt thu, cập nhật khoản thu trong đợt và đồng bộ theo đăng ký', TRUE),
  ('fee_collection_batches:delete', 'Xóa đợt thu',  'Xóa đợt thu chưa thu đồng nào', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'fee\_collection\_batches:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'fee\_collection\_batches:%'); DELETE FROM permissions WHERE code LIKE 'fee\_collection\_batches:%';

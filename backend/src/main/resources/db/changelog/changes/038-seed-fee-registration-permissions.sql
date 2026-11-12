--liquibase formatted sql

--changeset school-fee:038-seed-fee-registration-permissions
--comment Permissions for fee_registrations. There is no delete: unticking a cell is the unregistration.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('fee_registrations:read',  'Xem đăng ký thu', 'Xem khoản thu tự chọn mà học sinh đã đăng ký', TRUE),
  ('fee_registrations:write', 'Sửa đăng ký thu', 'Đăng ký và hủy đăng ký khoản thu tự chọn cho học sinh', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'fee\_registrations:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'fee\_registrations:%'); DELETE FROM permissions WHERE code LIKE 'fee\_registrations:%';

--liquibase formatted sql

--changeset school-fee:036-seed-fee-item-permissions
--comment Permissions for fee_items, granted like the other school catalogues.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('fee_items:read',   'Xem khoản thu', 'Xem danh sách và chi tiết khoản thu',         TRUE),
  ('fee_items:write',  'Sửa khoản thu', 'Thêm và sửa khoản thu, bảng giá và lịch thu', TRUE),
  ('fee_items:delete', 'Xóa khoản thu', 'Xóa khoản thu',                               TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'fee\_items:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'fee\_items:%'); DELETE FROM permissions WHERE code LIKE 'fee\_items:%';

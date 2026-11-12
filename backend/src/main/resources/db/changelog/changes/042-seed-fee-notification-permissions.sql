--liquibase formatted sql

--changeset school-fee:042-seed-fee-notification-permissions
--comment Permissions for fee_notifications. Delete cancels a notification that has not started sending.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('fee_notifications:read',   'Xem thông báo thu tiền',  'Xem các đợt thông báo thu tiền và kết quả gửi cho từng học sinh', TRUE),
  ('fee_notifications:write',  'Gửi thông báo thu tiền',  'Lập, gửi và gửi lại thông báo thu tiền cho phụ huynh', TRUE),
  ('fee_notifications:delete', 'Hủy thông báo thu tiền',  'Hủy thông báo thu tiền chưa bắt đầu gửi', TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'fee\_notifications:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'fee\_notifications:%'); DELETE FROM permissions WHERE code LIKE 'fee\_notifications:%';

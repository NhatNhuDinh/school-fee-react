--liquibase formatted sql

--changeset school-fee:032-seed-priority-category-permissions
--comment Permissions for priority_categories, granted like the other school catalogues.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('priority_categories:read',   'Xem diện ưu tiên', 'Xem danh sách và chi tiết diện ưu tiên', TRUE),
  ('priority_categories:write',  'Sửa diện ưu tiên', 'Thêm và sửa diện ưu tiên',               TRUE),
  ('priority_categories:delete', 'Xóa diện ưu tiên', 'Xóa diện ưu tiên',                       TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'priority\_categories:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'priority\_categories:%'); DELETE FROM permissions WHERE code LIKE 'priority\_categories:%';

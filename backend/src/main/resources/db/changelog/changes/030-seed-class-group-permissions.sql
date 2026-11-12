--liquibase formatted sql

--changeset school-fee:030-seed-class-group-permissions
--comment Permissions for class_groups and school_classes, granted like the other school catalogues.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('class_groups:read',    'Xem khối lớp',  'Xem danh sách và chi tiết khối lớp', TRUE),
  ('class_groups:write',   'Sửa khối lớp',  'Thêm và sửa khối lớp',               TRUE),
  ('class_groups:delete',  'Xóa khối lớp',  'Xóa khối lớp cùng các lớp của nó',   TRUE),
  ('school_classes:read',  'Xem lớp',       'Xem danh sách và chi tiết lớp',      TRUE),
  ('school_classes:write', 'Sửa lớp',       'Thêm và sửa lớp',                    TRUE),
  ('school_classes:delete','Xóa lớp',       'Xóa lớp',                            TRUE);

-- ADMIN and the school's own role, as for training_systems (028).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'class\_groups:%' OR p.code LIKE 'school\_classes:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'class\_groups:%' OR code LIKE 'school\_classes:%'); DELETE FROM permissions WHERE code LIKE 'class\_groups:%' OR code LIKE 'school\_classes:%';

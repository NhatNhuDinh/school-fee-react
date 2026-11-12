--liquibase formatted sql

--changeset school-fee:034-seed-student-permissions
--comment Permissions for students, granted like the other school catalogues.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('students:read',   'Xem học sinh', 'Xem danh sách và chi tiết học sinh', TRUE),
  ('students:write',  'Sửa học sinh', 'Thêm, sửa, xếp lớp và nhập học sinh từ năm trước', TRUE),
  ('students:delete', 'Xóa học sinh', 'Xóa học sinh khỏi năm học',         TRUE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'students:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'students:%'); DELETE FROM permissions WHERE code LIKE 'students:%';

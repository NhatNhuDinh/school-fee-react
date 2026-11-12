--liquibase formatted sql

--changeset school-fee:028-seed-school-catalogs
--comment Permissions for training_systems and measurement_units, granted like academic_years, and the Jmix rows for the two demo schools.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('training_systems:read',    'Xem hệ đào tạo',      'Xem danh sách và chi tiết hệ đào tạo',  TRUE),
  ('training_systems:write',   'Sửa hệ đào tạo',      'Thêm và sửa hệ đào tạo',                TRUE),
  ('training_systems:delete',  'Xóa hệ đào tạo',      'Xóa hệ đào tạo',                        TRUE),
  ('measurement_units:read',   'Xem đơn vị tính',     'Xem danh sách và chi tiết đơn vị tính', TRUE),
  ('measurement_units:write',  'Sửa đơn vị tính',     'Thêm và sửa đơn vị tính',               TRUE),
  ('measurement_units:delete', 'Xóa đơn vị tính',     'Xóa đơn vị tính',                       TRUE);

-- ADMIN and the school's own role, as for academic_years (019). A permission added
-- after 005 is granted to nobody until a migration says so.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code LIKE 'training\_systems:%' OR p.code LIKE 'measurement\_units:%'
WHERE r.code IN ('ADMIN', 'SCHOOL_CATALOG_MANAGER');

-- The rows the Jmix application held for THPT Nguyễn Bỉnh Khiêm (one training
-- system, sixteen units). The second demo school gets the same so that it has
-- something to show; nothing depends on it.
INSERT INTO training_systems (organization_id, name, created_at)
SELECT o.id, v.name, TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00'
FROM organizations o
CROSS JOIN (VALUES ('Chính quy')) AS v(name)
WHERE o.code IN ('NBK', 'CG')
ON CONFLICT ON CONSTRAINT uq_training_systems_organization_name DO NOTHING;

INSERT INTO measurement_units (organization_id, name, created_at)
SELECT o.id, v.name, TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00'
FROM organizations o
CROSS JOIN (VALUES ('Buổi'), ('Bộ'), ('Chiếc'), ('Cái'), ('Giờ'), ('Hoạt động'), ('Kỳ'), ('Ngày'), ('Năm'), ('Phút'), ('Quyển'), ('Quý'), ('Tháng'), ('Tiết'), ('Tuần'), ('Tờ')) AS v(name)
WHERE o.code IN ('NBK', 'CG')
ON CONFLICT ON CONSTRAINT uq_measurement_units_organization_name DO NOTHING;

--rollback DELETE FROM measurement_units; DELETE FROM training_systems; DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'training\_systems:%' OR code LIKE 'measurement\_units:%'); DELETE FROM permissions WHERE code LIKE 'training\_systems:%' OR code LIKE 'measurement\_units:%';

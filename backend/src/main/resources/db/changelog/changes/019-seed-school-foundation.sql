--liquibase formatted sql

--changeset school-fee:019-seed-school-foundation
--comment Permissions for academic_years, the school role, and two demo schools to prove they cannot see each other.

INSERT INTO permissions (code, name, description, system_permission) VALUES
  ('academic_years:read',   'View school years',            'List and open school years',                    TRUE),
  ('academic_years:write',  'Create and edit school years', 'Create school years and edit their terms',      TRUE),
  ('academic_years:delete', 'Delete school years',          'Permanently remove a school year',              TRUE);

-- The role a school's staff hold, ported from the Jmix "School Catalog Manager".
-- It is scoped to its own school by the tenant filter, not by anything granted here.
INSERT INTO roles (code, name, description, system_role) VALUES
  ('SCHOOL_CATALOG_MANAGER', 'School Catalog Manager',
   'Maintains the catalogues, students and fee collection of the school the account belongs to.', TRUE);

-- ADMIN is the system administrator and is the one role not scoped to a school.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('academic_years:read', 'academic_years:write', 'academic_years:delete')
WHERE r.code = 'ADMIN';

-- Reading the organization is how a school's staff resolve their own school.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
  'organizations:read',
  'academic_years:read', 'academic_years:write', 'academic_years:delete')
WHERE r.code = 'SCHOOL_CATALOG_MANAGER';

-- Development data: two schools and one account in each. The password of both
-- accounts is the development password of the seeded admin (Admin@123). Remove
-- this block, or change the passwords, on anything reachable by anyone else.
INSERT INTO organizations (code, name, status, created_at) VALUES
  ('NBK', 'THPT Nguyễn Bỉnh Khiêm', 'ACTIVE', TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00'),
  ('CG',  'THPT Cầu Giấy',          'ACTIVE', TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00');

INSERT INTO users (username, email, password_hash, full_name, status, created_at, organization_id)
SELECT v.username, v.email,
       '$2a$10$ff7CTQvwMv0Szb.qGXF3zeMUkV8Jjta52A6kYzGDeZY4YE2pyd.YS',
       v.full_name, 'ACTIVE', TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00', o.id
FROM (VALUES
  ('nbk.ketoan', 'ketoan@nbk.local', 'Kế toán NBK', 'NBK'),
  ('cg.ketoan',  'ketoan@cg.local',  'Kế toán CG',  'CG')
) AS v(username, email, full_name, org_code)
JOIN organizations o ON o.code = v.org_code;

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.code = 'SCHOOL_CATALOG_MANAGER'
WHERE u.username IN ('nbk.ketoan', 'cg.ketoan');

INSERT INTO academic_years (organization_id, name, start_date, end_date, created_at)
SELECT o.id, '2025-2026', DATE '2025-09-05', DATE '2026-05-27', TIMESTAMP WITH TIME ZONE '2024-01-01 00:00:00+00'
FROM organizations o WHERE o.code IN ('NBK', 'CG');

INSERT INTO academic_periods (academic_year_id, name, start_date, end_date)
SELECT y.id, t.name, t.start_date, t.end_date
FROM academic_years y
JOIN organizations o ON o.id = y.organization_id AND o.code IN ('NBK', 'CG')
CROSS JOIN (VALUES
  ('Học kỳ 1', DATE '2025-09-05', DATE '2026-01-18'),
  ('Học kỳ 2', DATE '2026-01-19', DATE '2026-05-27')
) AS t(name, start_date, end_date)
WHERE y.name = '2025-2026';

--rollback DELETE FROM academic_periods; DELETE FROM academic_years; DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE username IN ('nbk.ketoan','cg.ketoan')); DELETE FROM users WHERE username IN ('nbk.ketoan','cg.ketoan'); DELETE FROM organizations WHERE code IN ('NBK','CG'); DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE code = 'SCHOOL_CATALOG_MANAGER'); DELETE FROM roles WHERE code = 'SCHOOL_CATALOG_MANAGER'; DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'academic_years:%'); DELETE FROM permissions WHERE code LIKE 'academic_years:%';

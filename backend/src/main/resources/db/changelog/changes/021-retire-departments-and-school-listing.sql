--liquibase formatted sql

--changeset school-fee:021-retire-departments-and-school-listing
--comment Departments leave the product (no endpoint, no permission), and a school's staff stop being able to list schools.

-- Departments: the screens and the endpoint are gone, so the permissions that
-- guarded them go too. The table and the optional department on an account are
-- part of the user and row-level model and are not touched here.
DELETE FROM row_level_policy WHERE resource = 'departments';

DELETE FROM role_permissions
WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'departments:%');

DELETE FROM permissions WHERE code LIKE 'departments:%';

-- Listing schools is a system administrator's job. A school's staff resolve
-- their own school server-side and never need to read the others, so the grant
-- 019 gave them was wider than anything they use.
DELETE FROM role_permissions
WHERE role_id = (SELECT id FROM roles WHERE code = 'SCHOOL_CATALOG_MANAGER')
  AND permission_id = (SELECT id FROM permissions WHERE code = 'organizations:read');

--rollback empty

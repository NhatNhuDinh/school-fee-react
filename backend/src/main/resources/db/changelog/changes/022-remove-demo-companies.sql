--liquibase formatted sql

--changeset school-fee:022-remove-demo-companies
--comment Removes the framework's example companies, their departments and the two example accounts, leaving only schools and the system administrator.

-- The system administrator belongs to no school: it administers all of them.
-- Both columns together, because a department without an organization is the
-- state the CHECK on users forbids.
UPDATE users SET department_id = NULL, organization_id = NULL WHERE username = 'admin';

-- The example accounts. Every table that points at users cascades or nulls, so
-- their sessions, notifications and role links go with them.
DELETE FROM users WHERE username IN ('manager', 'viewer');

DELETE FROM departments
WHERE organization_id IN (SELECT id FROM organizations WHERE code IN ('DTH', 'DTHS', 'DTHC', 'DTHL'));

DELETE FROM organizations WHERE code IN ('DTH', 'DTHS', 'DTHC', 'DTHL');

--rollback empty

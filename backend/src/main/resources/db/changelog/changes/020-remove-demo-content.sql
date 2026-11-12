--liquibase formatted sql

--changeset school-fee:020-remove-demo-content
--comment Removes what the framework shipped as examples: blog posts, categories and tasks, with their permissions, grants and row-level policies.

-- Policies first: a row-level policy names its resource by string, and no
-- foreign key would stop one outliving the table it described.
DELETE FROM row_level_policy WHERE resource IN ('blog_posts', 'categories', 'tasks');

DELETE FROM role_permissions
WHERE permission_id IN (
  SELECT id FROM permissions
  WHERE code LIKE 'blog_posts:%' OR code LIKE 'categories:%' OR code LIKE 'tasks:%');

DELETE FROM permissions
WHERE code LIKE 'blog_posts:%' OR code LIKE 'categories:%' OR code LIKE 'tasks:%';

-- Children before parents: blog_posts reference categories.
DROP TABLE IF EXISTS blog_posts;
DROP TABLE IF EXISTS categories;
DROP TABLE IF EXISTS tasks;

--rollback empty

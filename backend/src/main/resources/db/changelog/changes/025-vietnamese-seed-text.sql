--liquibase formatted sql

--changeset school-fee:025-vietnamese-seed-text
--comment Vietnamese names and descriptions for the seeded permissions, roles and administrator account. Codes are untouched.

UPDATE permissions AS p SET name = v.name, description = v.description
FROM (VALUES
  ('users:read',               'Xem người dùng',            'Xem danh sách và chi tiết tài khoản người dùng'),
  ('users:write',              'Thêm và sửa người dùng',    'Tạo tài khoản, chỉnh sửa, gán vai trò và đặt lại mật khẩu'),
  ('users:delete',             'Xóa người dùng',            'Xóa vĩnh viễn một tài khoản người dùng'),
  ('roles:read',               'Xem vai trò',               'Xem danh sách vai trò và các quyền mà vai trò được cấp'),
  ('roles:write',              'Thêm và sửa vai trò',       'Tạo vai trò và thay đổi các quyền mà vai trò được cấp'),
  ('roles:delete',             'Xóa vai trò',               'Xóa vĩnh viễn một vai trò'),
  ('permissions:read',         'Xem quyền chức năng',       'Xem danh mục quyền chức năng'),
  ('notifications:read',       'Xem thông báo',             'Xem thông báo của chính mình và đánh dấu đã đọc'),
  ('notifications:delete',     'Xóa thông báo',             'Xóa vĩnh viễn một thông báo của chính mình'),
  ('organizations:read',       'Xem đơn vị',                'Xem danh sách và chi tiết đơn vị'),
  ('organizations:write',      'Thêm và sửa đơn vị',        'Tạo đơn vị và chỉnh sửa thông tin đơn vị'),
  ('organizations:delete',     'Xóa đơn vị',                'Xóa vĩnh viễn một đơn vị'),
  ('row_level_policies:read',  'Xem quyền dữ liệu',         'Xem mỗi vai trò được truy cập những dữ liệu nào'),
  ('row_level_policies:write', 'Sửa quyền dữ liệu',         'Thu hẹp hoặc mở rộng phạm vi dữ liệu của vai trò'),
  ('row_level_policies:delete','Xóa quyền dữ liệu',         'Xóa một chính sách quyền dữ liệu'),
  ('academic_years:read',      'Xem năm học',               'Xem danh sách và chi tiết năm học'),
  ('academic_years:write',     'Thêm và sửa năm học',       'Tạo năm học và chỉnh sửa các học kỳ của năm học'),
  ('academic_years:delete',    'Xóa năm học',               'Xóa vĩnh viễn một năm học')
) AS v(code, name, description)
WHERE p.code = v.code;

UPDATE roles AS r SET name = v.name, description = v.description
FROM (VALUES
  ('ADMIN',                  'Quản trị hệ thống',           'Toàn quyền trên mọi phần của hệ thống, bao gồm người dùng và vai trò.'),
  ('MANAGER',                'Quản lý',                     'Quản lý nội dung và được xem, nhưng không được sửa, người dùng và vai trò.'),
  ('USER',                   'Người dùng',                  'Chỉ được xem nội dung.'),
  ('SCHOOL_CATALOG_MANAGER', 'Quản lý danh mục của trường', 'Quản lý danh mục, học sinh và việc thu phí của trường mà tài khoản thuộc về.')
) AS v(code, name, description)
WHERE r.code = v.code;

UPDATE users SET full_name = 'Quản trị hệ thống' WHERE username = 'admin' AND full_name = 'System Administrator';

--rollback empty

-- 登记项目删除权限（仅系统管理员角色）

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 4100, 4, '删除登记项目', 3, NULL, NULL, 'exchange:project:delete', NULL, 99, 'M040', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'exchange:project:delete');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE permission = 'exchange:project:delete' LIMIT 1;

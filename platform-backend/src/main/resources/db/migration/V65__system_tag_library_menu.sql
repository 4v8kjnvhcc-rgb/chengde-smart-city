-- 系统管理：标签管理（标准标签库维护入口）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6300, 19, '标签管理', 2, '/system/tags', 'system/TagLibraryManage', 'system:tag:list', NULL, 33, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6300 OR path = '/system/tags');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6301, 6300, '标签查询', 3, NULL, NULL, 'system:tag:query', NULL, 1, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:tag:query');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6302, 6300, '标签维护', 3, NULL, NULL, 'system:tag:edit', NULL, 2, NULL, 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'system:tag:edit');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id BETWEEN 6300 AND 6302
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

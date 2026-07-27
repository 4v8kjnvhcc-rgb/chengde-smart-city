-- 角色管理：增删改按钮权限（挂在角色管理菜单 id=25 下）

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6510, 25, '角色新增', 3, NULL, NULL, 'system:role:add', NULL, 1, 'M211', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6510 OR permission = 'system:role:add');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6511, 25, '角色编辑', 3, NULL, NULL, 'system:role:edit', NULL, 2, 'M211', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6511 OR permission = 'system:role:edit');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6512, 25, '角色删除', 3, NULL, NULL, 'system:role:delete', NULL, 3, 'M211', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6512 OR permission = 'system:role:delete');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (6510, 6511, 6512)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

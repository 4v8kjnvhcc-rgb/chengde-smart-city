-- 恢复系统管理员角色菜单授权（误勾选导致门户只剩归集入口）
-- 并为菜单管理等入口再次确保启用

UPDATE sys_menu
SET status = 1,
    visible = 1,
    parent_id = 6402,
    sort_order = 31,
    menu_name = '菜单管理',
    path = '/system/menus',
    component = 'system/MenuManage',
    permission = 'system:menu:list',
    integration_type = 'self'
WHERE id = 27;

UPDATE sys_menu SET status = 1, visible = 1 WHERE id IN (1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 6400, 6401, 6402, 6403, 6500);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.status = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

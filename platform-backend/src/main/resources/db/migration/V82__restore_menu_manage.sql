-- 恢复「菜单管理」页面入口（V72 曾从侧栏隐藏）
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

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 27 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 27);

-- 确保平台运维分组目录启用
UPDATE sys_menu SET status = 1, visible = 1 WHERE id = 6402;

-- 角色菜单配置中不展示「删除登记项目」按钮（业务页仍可用接口权限，不再作为可勾选菜单项）
UPDATE sys_menu
SET status = 0
WHERE id = 4100
   OR permission = 'exchange:project:delete';

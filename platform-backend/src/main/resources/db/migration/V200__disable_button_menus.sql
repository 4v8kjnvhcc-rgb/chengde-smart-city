-- V200: 取消按钮级菜单权限配置——停用全部 menu_type=3，并从角色授权中移除
-- 说明：按钮节点不再出现在「配置菜单」树；API 所需旧按钮 permission
-- 在用户拥有其父级菜单时由 findPermissionsByUserId 自动继承（见 SysUserMapper）。

-- 先清角色勾选
DELETE rm FROM sys_role_menu rm
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.menu_type = 3;

-- 停用全部按钮菜单（保留行便于 parent 继承 permission，不进配置树）
UPDATE sys_menu
SET status = 0,
    visible = 0
WHERE menu_type = 3;

-- R88：清理角色菜单中「有下级的父目录」授权行
-- 背景：全选目录时父 id 曾被写入 sys_role_menu；Hub 父节点常 visible=0，
--       导致新用户登录后按血缘裁掉已授权子项（有的显示有的不显示）。
-- 目标库：smart_city（控制面）
-- 可重复执行：是（仅删仍满足「有下级」的 menu_id）

DELETE rm
FROM sys_role_menu rm
INNER JOIN (
    SELECT DISTINCT parent_id AS id
    FROM sys_menu
    WHERE parent_id IS NOT NULL
      AND parent_id <> 0
      AND IFNULL(status, 1) <> 0
) parents ON rm.menu_id = parents.id;

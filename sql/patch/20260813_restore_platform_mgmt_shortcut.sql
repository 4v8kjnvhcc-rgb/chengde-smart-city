-- V207: 平台管理一级目录（无子菜单）— 菜单可勾选、主页出卡、点击进统一用户
-- 子树 6400/6401/6402 等保持停用，配置树不展示下级

UPDATE sys_menu
SET parent_id = 1,
    menu_name = '平台管理',
    menu_type = 1,
    path = '/system',
    component = NULL,
    permission = 'hub:system:platform',
    icon = NULL,
    sort_order = 90,
    m_code = NULL,
    integration_type = 'self',
    status = 1,
    visible = 1
WHERE id = 19;

-- 确保存在（极端环境无 id=19）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 19, 1, '平台管理', 1, '/system', NULL, 'hub:system:platform', NULL, 90, NULL, 'self', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 19);

-- 下级一律停用（不在菜单管理/配置菜单中展示）
UPDATE sys_menu
SET status = 0, visible = 0
WHERE id IN (27, 6400, 6401, 6402, 6403, 6500)
   OR parent_id IN (19, 6400, 6401, 6402)
   OR (path LIKE '/system/%' AND path <> '/system');

-- SYSTEM_ADMIN 授平台管理入口
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 19
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 19 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 19);

-- 曾授旧平台管理子树的角色，补授一级入口
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 19
FROM sys_role_menu rm
WHERE rm.menu_id IN (27, 6400, 6401, 6402, 6403, 6500)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 19);

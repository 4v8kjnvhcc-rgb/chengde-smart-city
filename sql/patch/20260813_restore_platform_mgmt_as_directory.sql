-- 手工补丁（对应 Flyway V213）：平台管理恢复为仅目录快捷入口
-- 目标库：smart_city
-- 可重复执行

UPDATE sys_menu
SET parent_id = 1,
    menu_name = '平台管理',
    menu_type = 1,
    path = '/system',
    component = NULL,
    permission = 'hub:system:platform',
    icon = NULL,
    sort_order = 90,
    integration_type = 'self',
    status = 1,
    visible = 1
WHERE id = 19;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 19, 1, '平台管理', 1, '/system', NULL, 'hub:system:platform', NULL, 90, NULL, 'self', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 19);

UPDATE sys_menu
SET status = 0, visible = 0
WHERE id IN (27, 6400, 6401, 6402, 6403, 6500, 6001, 6002, 6003, 6004, 6005)
   OR parent_id IN (19, 6400, 6401, 6402)
   OR (IFNULL(path, '') LIKE '/system/%' AND path <> '/system');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 19
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 19 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 19);

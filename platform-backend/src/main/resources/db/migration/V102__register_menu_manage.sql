-- V102: 数据资产登记管理系统 · 菜单管理
ALTER TABLE sys_menu
    ADD COLUMN route_name VARCHAR(64) NULL COMMENT '菜单英文名称/路由名' AFTER menu_name;

UPDATE sys_menu
SET route_name = COALESCE(NULLIF(m_code, ''), SUBSTRING_INDEX(permission, ':', -1))
WHERE parent_id = 7000
  AND (route_name IS NULL OR route_name = '');

UPDATE sys_menu
SET route_name = 'RegisterRoot'
WHERE id = 7000
  AND (route_name IS NULL OR route_name = '');

INSERT INTO sys_menu (id, parent_id, menu_name, route_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7015, 7000, '菜单管理', 'MenuManage', 2,
       '/exchange/ingestion?system=register&module=menu-mgmt',
       'exchange/ingestion/register/RegisterMenuManageView',
       'hub:ingestion:register:menu-mgmt',
       NULL, 15, 'MMENU', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7015 OR permission = 'hub:ingestion:register:menu-mgmt');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE (m.id = 7015 OR m.permission = 'hub:ingestion:register:menu-mgmt')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- V202: 供需侧栏与角色配置菜单对齐
-- 1) 「供需配置/事项管理」挂到「系统管理」目录下（与 SupplyAppView 侧栏一致）
-- 2) 路径统一为 /exchange/application/supply?...

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7859, 7130, '系统管理', 1, NULL, NULL, 'hub:application:supply:system', NULL, 8, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7859 OR permission = 'hub:application:supply:system');

UPDATE sys_menu
SET parent_id = 7859,
    sort_order = 1,
    path = '/exchange/application/supply?section=supply-config',
    menu_name = '供需配置',
    status = 1,
    visible = 0,
    integration_type = 'hub'
WHERE id = 7857 OR permission = 'hub:application:supply:config';

UPDATE sys_menu
SET parent_id = 7859,
    sort_order = 2,
    path = '/exchange/application/supply?section=matter-manage',
    menu_name = '事项管理',
    status = 1,
    visible = 0,
    integration_type = 'hub'
WHERE id = 7858 OR permission = 'hub:application:supply:matter';

UPDATE sys_menu SET path = '/exchange/application/supply?section=home', sort_order = 1
WHERE id = 7850 OR permission = 'hub:application:supply:home';
UPDATE sys_menu SET path = '/exchange/application/supply?section=demand', sort_order = 2
WHERE id = 7851 OR permission = 'hub:application:supply:demand';
UPDATE sys_menu SET path = '/exchange/application/supply?section=analysis', sort_order = 3
WHERE id = 7852 OR permission = 'hub:application:supply:analysis';
UPDATE sys_menu SET path = '/exchange/application/supply?section=confirm', sort_order = 4
WHERE id = 7853 OR permission = 'hub:application:supply:confirm';
UPDATE sys_menu SET path = '/exchange/application/supply?section=supply', sort_order = 5
WHERE id = 7854 OR permission = 'hub:application:supply:supply';
UPDATE sys_menu SET path = '/exchange/application/supply?section=supervise', sort_order = 6
WHERE id = 7855 OR permission = 'hub:application:supply:supervise';
UPDATE sys_menu SET path = '/exchange/application/supply?section=manifest-center', sort_order = 7
WHERE id = 7856 OR permission = 'hub:application:supply:manifest';

UPDATE sys_menu SET sort_order = 8 WHERE id = 7859;

-- 父级 7130 入口
UPDATE sys_menu
SET path = '/exchange/application/supply',
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '数据供需对接系统'
WHERE id = 7130;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7859, 7850, 7851, 7852, 7853, 7854, 7855, 7856, 7857, 7858, 7130)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

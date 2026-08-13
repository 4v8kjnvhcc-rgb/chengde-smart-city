-- 手工补丁：数据融合治理平台 — 「数据指标」一级菜单 + 三子菜单（与 Flyway V211 同内容）
-- 目标库：smart_city（控制面）
-- 可重复执行

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7250, 9, '数据指标', 1, NULL, NULL, 'hub:gov:indicator', NULL, 60, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7250 OR permission = 'hub:gov:indicator');

UPDATE sys_menu
SET parent_id = 9,
    menu_name = '数据指标',
    menu_type = 1,
    path = NULL,
    component = NULL,
    permission = 'hub:gov:indicator',
    sort_order = 60,
    integration_type = 'hub',
    status = 1,
    visible = 0
WHERE id = 7250;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7251, 7250, '指标域管理', 2, '/governance?tab=indicator&iSub=domains', NULL,
       'hub:gov:indicator:domains', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7251 OR permission = 'hub:gov:indicator:domains');

UPDATE sys_menu
SET parent_id = 7250,
    menu_name = '指标域管理',
    menu_type = 2,
    path = '/governance?tab=indicator&iSub=domains',
    permission = 'hub:gov:indicator:domains',
    sort_order = 1,
    integration_type = 'hub',
    status = 1,
    visible = 0
WHERE id = 7251;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7252, 7250, '指标组管理', 2, '/governance?tab=indicator&iSub=groups', NULL,
       'hub:gov:indicator:groups', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7252 OR permission = 'hub:gov:indicator:groups');

UPDATE sys_menu
SET parent_id = 7250,
    menu_name = '指标组管理',
    menu_type = 2,
    path = '/governance?tab=indicator&iSub=groups',
    permission = 'hub:gov:indicator:groups',
    sort_order = 2,
    integration_type = 'hub',
    status = 1,
    visible = 0
WHERE id = 7252;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7253, 7250, '指标任务', 2, '/governance?tab=indicator&iSub=tasks', NULL,
       'hub:gov:indicator:tasks', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7253 OR permission = 'hub:gov:indicator:tasks');

UPDATE sys_menu
SET parent_id = 7250,
    menu_name = '指标任务',
    menu_type = 2,
    path = '/governance?tab=indicator&iSub=tasks',
    permission = 'hub:gov:indicator:tasks',
    sort_order = 3,
    integration_type = 'hub',
    status = 1,
    visible = 0
WHERE id = 7253;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
INNER JOIN sys_menu exist ON exist.id = rm.menu_id AND exist.status = 1
  AND (exist.id BETWEEN 7200 AND 7249 OR exist.permission LIKE 'hub:gov:%')
CROSS JOIN sys_menu m
WHERE m.id IN (7250, 7251, 7252, 7253)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.id IN (7250, 7251, 7252, 7253)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

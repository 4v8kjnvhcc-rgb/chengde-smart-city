-- V204: 大数据挖掘分析平台「配置菜单」对齐门户飞出
-- 门户（portal_nav）：通用支撑→统一用户/智能BI/任务管理/集成运维；业务支撑→人口/法人/宏观/重点
-- 原 sys_menu：12 下直接挂 通用支撑(Hub细项)/智能BI/人口…，与门户两级结构不一致

-- ========== A. 门户同构两级目录 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7880, 12, '通用支撑平台', 1, '/analytics/general-support', NULL,
       'hub:analytics:general-support', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7880 OR permission = 'hub:analytics:general-support');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7881, 12, '业务支撑平台', 1, '/analytics/business-support', NULL,
       'hub:analytics:business-support', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7881 OR permission = 'hub:analytics:business-support');

-- ========== B. 通用支撑下四入口（与门户一致） ==========
-- 原 id=13「通用支撑平台」→「统一用户管理系统」（Hub 根，侧栏仍为用户中心…）
UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 1,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '统一用户管理系统',
    path = '/analytics/support',
    permission = COALESCE(NULLIF(permission, ''), 'hub:analytics:support')
WHERE id = 13;

UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 2,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '智能BI平台',
    path = '/analytics/bi'
WHERE id = 14;

-- 任务管理 / 集成运维：门户与「统一用户」并列，从 Hub 一级挪到通用支撑下
UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 3,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '任务管理',
    path = '/analytics/support?tab=tasks'
WHERE id = 7507;

UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 4,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '集成运维',
    path = '/analytics/support?tab=ops.kettle'
WHERE id = 7508;

-- UUM Hub 侧栏目录仍挂在「统一用户管理系统」下（不含任务/运维）
UPDATE sys_menu SET parent_id = 13, sort_order = 1, menu_name = '用户中心', status = 1, visible = 0 WHERE id = 7500;
UPDATE sys_menu SET parent_id = 13, sort_order = 2, menu_name = '应用中心', status = 1, visible = 0 WHERE id = 7501;
UPDATE sys_menu SET parent_id = 13, sort_order = 3, menu_name = '认证中心', status = 1, visible = 0 WHERE id = 7502;
UPDATE sys_menu SET parent_id = 13, sort_order = 4, menu_name = '服务中心', status = 1, visible = 0 WHERE id = 7503;
UPDATE sys_menu SET parent_id = 13, sort_order = 5, menu_name = '系统管理', status = 1, visible = 0 WHERE id = 7504;
UPDATE sys_menu SET parent_id = 13, sort_order = 6, menu_name = '日志审计', status = 1, visible = 0 WHERE id = 7505;
UPDATE sys_menu SET parent_id = 13, sort_order = 7, menu_name = '其他', status = 1, visible = 0 WHERE id = 7506;

-- ========== C. 业务支撑下四系统 ==========
UPDATE sys_menu
SET parent_id = 7881,
    sort_order = 1,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '人口大数据支撑系统',
    path = '/analytics/population'
WHERE id = 15;

UPDATE sys_menu
SET parent_id = 7881,
    sort_order = 2,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '法人大数据支撑系统',
    path = '/analytics/legal-entity'
WHERE id = 16;

UPDATE sys_menu
SET parent_id = 7881,
    sort_order = 3,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '宏观经济及工业运行大数据支撑系统',
    path = '/analytics/macro'
WHERE id = 17;

UPDATE sys_menu
SET parent_id = 7881,
    sort_order = 4,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '重点领域示范应用支撑系统',
    path = '/analytics/key-domains'
WHERE id = 18;

-- ========== D. 清理：挖掘分析平台下仅保留通用/业务两目录 ==========
UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 12
  AND id NOT IN (7880, 7881)
  AND status = 1;

-- ========== E. 授权迁移 ==========
-- 曾授通用支撑相关 → 补授 7880
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7880
FROM sys_role_menu rm
INNER JOIN sys_menu oldm ON oldm.id = rm.menu_id
WHERE (
    oldm.id IN (13, 14, 7507, 7508)
    OR oldm.id BETWEEN 7500 AND 7506
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:support%'
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:bi%'
    OR IFNULL(oldm.path, '') LIKE '/analytics/support%'
    OR IFNULL(oldm.path, '') LIKE '/analytics/bi%'
  )
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 7880 AND m.status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7880);

-- 曾授业务域系统 → 补授 7881
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7881
FROM sys_role_menu rm
INNER JOIN sys_menu oldm ON oldm.id = rm.menu_id
WHERE (
    oldm.id IN (15, 16, 17, 18)
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:population%'
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:legal%'
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:macro%'
    OR IFNULL(oldm.permission, '') LIKE 'hub:analytics:key%'
    OR IFNULL(oldm.path, '') IN ('/analytics/population', '/analytics/legal-entity', '/analytics/macro', '/analytics/key-domains')
    OR IFNULL(oldm.path, '') LIKE '/analytics/population%'
    OR IFNULL(oldm.path, '') LIKE '/analytics/legal-entity%'
    OR IFNULL(oldm.path, '') LIKE '/analytics/macro%'
    OR IFNULL(oldm.path, '') LIKE '/analytics/key-domains%'
  )
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 7881 AND m.status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7881);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7880, 7881, 12, 13, 14, 15, 16, 17, 18, 7507, 7508)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- ========== F. 门户导航名称对齐（防漂移） ==========
UPDATE portal_nav_node SET name = '统一用户管理系统', url = '/analytics/support', menu_path = '/analytics/support' WHERE id = 311;
UPDATE portal_nav_node SET name = '智能BI平台', url = '/analytics/bi', menu_path = '/analytics/bi' WHERE id = 312;
UPDATE portal_nav_node SET name = '任务管理', url = '/analytics/support?tab=tasks', menu_path = '/analytics/support?tab=tasks' WHERE id = 313;
UPDATE portal_nav_node SET name = '集成运维', url = '/analytics/support?tab=ops.kettle', menu_path = '/analytics/support?tab=ops.kettle' WHERE id = 314;
UPDATE portal_nav_node SET name = '人口大数据支撑系统', url = '/analytics/population', menu_path = '/analytics/population' WHERE id = 321;
UPDATE portal_nav_node SET name = '法人大数据支撑系统', url = '/analytics/legal-entity', menu_path = '/analytics/legal-entity' WHERE id = 322;
UPDATE portal_nav_node SET name = '宏观经济及工业运行大数据支撑系统', url = '/analytics/macro', menu_path = '/analytics/macro' WHERE id = 323;
UPDATE portal_nav_node SET name = '重点领域示范应用支撑系统', url = '/analytics/key-domains', menu_path = '/analytics/key-domains' WHERE id = 324;
UPDATE portal_nav_node SET name = '通用支撑平台' WHERE id = 31;
UPDATE portal_nav_node SET name = '业务支撑平台' WHERE id = 32;

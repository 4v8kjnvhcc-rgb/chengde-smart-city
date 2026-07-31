-- R36 纠偏：交换平台一级仅 4 入口（归集 / ESB / 应用分析门户 / 应用平台）
-- 子入口在各自 Hub 内展开，不在 Dashboard 抽屉摊平

-- 应用分析门户：一级可进 Hub
UPDATE sys_menu
SET menu_name = '应用分析门户',
    menu_type = 2,
    path = '/exchange/analysis-portal',
    component = 'exchange/application/AnalysisPortalHubView',
    integration_type = 'self',
    status = 1,
    sort_order = 6,
    parent_id = 3
WHERE id = 7;

-- 应用平台：一级可进 Hub（排在分析门户之后）
UPDATE sys_menu
SET menu_name = '应用平台',
    menu_type = 2,
    path = '/exchange/application',
    component = 'exchange/application/ApplicationHubView',
    integration_type = 'self',
    status = 1,
    sort_order = 7,
    parent_id = 3
WHERE id = 6;

-- Hub 内子入口（供菜单树/权限；Dashboard 不展开）
UPDATE sys_menu
SET parent_id = 7,
    path = '/exchange/analysis-portal?portal=dept&section=home',
    menu_name = '部门数据共享门户',
    menu_type = 2,
    status = 1,
    sort_order = 1
WHERE id = 7120;

UPDATE sys_menu
SET parent_id = 7,
    path = '/exchange/analysis-portal?portal=leader',
    menu_name = '领导决策门户',
    menu_type = 2,
    status = 1,
    sort_order = 2
WHERE id = 7121;

UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application?app=supply',
    menu_name = '数据供需对接系统',
    status = 1,
    sort_order = 1
WHERE id = 7130;

UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application?app=assessment',
    menu_name = '考核评估系统',
    status = 1,
    sort_order = 2
WHERE id = 7131;

UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application?app=stats-base&section=base',
    menu_name = '基础库统计分析应用',
    status = 1,
    sort_order = 3
WHERE id = 7132;

UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application?app=stats-domain&section=domain',
    menu_name = '重点领域统计分析应用',
    status = 1,
    sort_order = 4
WHERE id = 7133;

-- 配置页不进 Dashboard 一级；挂应用平台下且默认隐藏于侧栏摊平
UPDATE sys_menu SET parent_id = 6, visible = 0, sort_order = 90 WHERE id = 6102;
UPDATE sys_menu SET parent_id = 6, visible = 0, sort_order = 91 WHERE id = 6103;

-- 确保子菜单存在（兼容未跑过插入的环境）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7120, 7, '部门数据共享门户', 2, '/exchange/analysis-portal?portal=dept&section=home', NULL, 'hub:analysis:dept', NULL, 1, 'M032', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7120);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7121, 7, '领导决策门户', 2, '/exchange/analysis-portal?portal=leader', NULL, 'hub:analysis:leader', NULL, 2, 'M036', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7121);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7130, 6, '数据供需对接系统', 2, '/exchange/application?app=supply', NULL, 'hub:application:supply', NULL, 1, 'M020', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7130);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7131, 6, '考核评估系统', 2, '/exchange/application?app=assessment', NULL, 'hub:application:assessment', NULL, 2, 'M030', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7131);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7132, 6, '基础库统计分析应用', 2, '/exchange/application?app=stats-base&section=base', NULL, 'analytics:stats:view', NULL, 3, 'M037', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7132);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7133, 6, '重点领域统计分析应用', 2, '/exchange/application?app=stats-domain&section=domain', NULL, 'analytics:stats:view', NULL, 4, 'M038', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7133);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
CROSS JOIN (SELECT 7120 AS id UNION ALL SELECT 7121 UNION ALL SELECT 7130 UNION ALL SELECT 7131 UNION ALL SELECT 7132 UNION ALL SELECT 7133) m
WHERE rm.menu_id IN (6, 7)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
  );

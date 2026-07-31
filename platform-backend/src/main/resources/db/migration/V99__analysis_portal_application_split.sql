-- R36: 应用分析门户与应用平台拆分
-- 交换平台：归集 / ESB / 应用分析门户(部门+领导) / 应用平台(四应用)

-- 1) 恢复「应用分析门户」为目录，挂在交换平台下
UPDATE sys_menu
SET menu_name = '应用分析门户',
    menu_type = 1,
    path = NULL,
    component = NULL,
    integration_type = 'self',
    status = 1,
    sort_order = 7,
    parent_id = 3
WHERE id = 7;

-- 2) 部门数据共享门户 / 领导决策门户
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7120, 7, '部门数据共享门户', 2, '/exchange/analysis-portal/dept?section=home', NULL, 'hub:analysis:dept', NULL, 1, 'M032', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7120);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 7121, 7, '领导决策门户', 2, '/exchange/analysis-portal/leader', NULL, 'hub:analysis:leader', NULL, 2, 'M036', 'self', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7121);

-- 3) 应用平台保持页级入口，子入口改为四应用
UPDATE sys_menu
SET menu_name = '应用平台',
    menu_type = 1,
    path = NULL,
    component = NULL,
    integration_type = 'self',
    status = 1,
    sort_order = 6,
    parent_id = 3
WHERE id = 6;

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

-- 4) 停用旧 Hub 细菜单（门户五 Tab / 统计 / 驾驶舱挂在应用平台下）
UPDATE sys_menu SET status = 0 WHERE id IN (7100, 7101, 7102, 7103, 7104, 7105, 7110, 7111);

-- 5) 旧「考核评估(已并入…)」菜单停用
UPDATE sys_menu SET status = 0 WHERE id = 35;

-- 6) 供需配置仍挂应用平台下
UPDATE sys_menu SET parent_id = 6, status = 1, sort_order = 10 WHERE id = 6102;
UPDATE sys_menu SET parent_id = 6, status = 1, sort_order = 11 WHERE id = 6103;

-- 7) 授权：凡已有应用平台(6)或旧分析门户(7)菜单的角色，同步新入口
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
CROSS JOIN (SELECT 7120 AS id UNION ALL SELECT 7121 UNION ALL SELECT 7130 UNION ALL SELECT 7131 UNION ALL SELECT 7132 UNION ALL SELECT 7133) m
WHERE rm.menu_id IN (6, 7)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
  );

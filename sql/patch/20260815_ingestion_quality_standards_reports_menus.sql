-- V229: 汇聚「汇聚数据质量管控」补齐标书两模块：数据标准体系 + 数据质量分析报告
-- 号段 7890～7895（避开 7838～7843 已占用）；与治理侧 V228 对称；角色授权同步

-- 父节点保持目录
UPDATE sys_menu
SET parent_id = 7020,
    menu_name = '汇聚数据质量管控',
    menu_type = 1,
    path = NULL,
    component = NULL,
    permission = 'hub:ingestion:collect:quality',
    sort_order = 4,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7024;

-- 数据标准体系（目录）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7890, 7024, '数据标准体系', 1, NULL, NULL, 'hub:ingestion:collect:quality:standards', NULL, 1, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7890 OR permission = 'hub:ingestion:collect:quality:standards');

UPDATE sys_menu
SET parent_id = 7024,
    menu_name = '数据标准体系',
    menu_type = 1,
    path = NULL,
    permission = 'hub:ingestion:collect:quality:standards',
    sort_order = 1,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7890;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7891, 7890, '标准文件管理', 2, '/exchange/ingestion?system=collect&module=quality.standards.file', NULL,
       'hub:ingestion:collect:quality:standards:file', NULL, 1, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7891 OR permission = 'hub:ingestion:collect:quality:standards:file');

UPDATE sys_menu
SET parent_id = 7890, menu_name = '标准文件管理', menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=quality.standards.file',
    permission = 'hub:ingestion:collect:quality:standards:file',
    sort_order = 1, integration_type = 'hub', status = 1, visible = 1
WHERE id = 7891;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7892, 7890, '信息数据元规范', 2, '/exchange/ingestion?system=collect&module=quality.standards.element', NULL,
       'hub:ingestion:collect:quality:standards:element', NULL, 2, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7892 OR permission = 'hub:ingestion:collect:quality:standards:element');

UPDATE sys_menu
SET parent_id = 7890, menu_name = '信息数据元规范', menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=quality.standards.element',
    permission = 'hub:ingestion:collect:quality:standards:element',
    sort_order = 2, integration_type = 'hub', status = 1, visible = 1
WHERE id = 7892;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7893, 7890, '数据编码规范', 2, '/exchange/ingestion?system=collect&module=quality.standards.code', NULL,
       'hub:ingestion:collect:quality:standards:code', NULL, 3, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7893 OR permission = 'hub:ingestion:collect:quality:standards:code');

UPDATE sys_menu
SET parent_id = 7890, menu_name = '数据编码规范', menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=quality.standards.code',
    permission = 'hub:ingestion:collect:quality:standards:code',
    sort_order = 3, integration_type = 'hub', status = 1, visible = 1
WHERE id = 7893;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7894, 7890, '命名规范', 2, '/exchange/ingestion?system=collect&module=quality.standards.naming', NULL,
       'hub:ingestion:collect:quality:standards:naming', NULL, 4, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7894 OR permission = 'hub:ingestion:collect:quality:standards:naming');

UPDATE sys_menu
SET parent_id = 7890, menu_name = '命名规范', menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=quality.standards.naming',
    permission = 'hub:ingestion:collect:quality:standards:naming',
    sort_order = 4, integration_type = 'hub', status = 1, visible = 1
WHERE id = 7894;

-- 原三叶排序后移；确保启用可见
UPDATE sys_menu SET sort_order = 2, status = 1, visible = 1,
    path = '/exchange/ingestion?system=collect&module=quality.rule-config',
    permission = 'hub:ingestion:collect:quality:rule-config'
WHERE id = 7820;
UPDATE sys_menu SET sort_order = 3, status = 1, visible = 1,
    path = '/exchange/ingestion?system=collect&module=quality.monitor',
    permission = 'hub:ingestion:collect:quality:monitor'
WHERE id = 7821;
UPDATE sys_menu SET sort_order = 4, status = 1, visible = 1,
    path = '/exchange/ingestion?system=collect&module=quality.assess',
    permission = 'hub:ingestion:collect:quality:assess'
WHERE id = 7822;

-- 数据质量分析报告
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7895, 7024, '数据质量分析报告', 2, '/exchange/ingestion?system=collect&module=quality.reports', NULL,
       'hub:ingestion:collect:quality:reports', NULL, 5, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7895 OR permission = 'hub:ingestion:collect:quality:reports');

UPDATE sys_menu
SET parent_id = 7024,
    menu_name = '数据质量分析报告',
    menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=quality.reports',
    permission = 'hub:ingestion:collect:quality:reports',
    sort_order = 5,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7895;

-- 角色授权：已有汇聚质量任一菜单的角色 + SYSTEM_ADMIN
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
INNER JOIN sys_menu exist ON exist.id = rm.menu_id AND exist.status = 1
  AND (
    exist.id IN (7024, 7820, 7821, 7822)
    OR exist.permission LIKE 'hub:ingestion:collect:quality%'
  )
CROSS JOIN sys_menu m
WHERE m.id IN (7890, 7891, 7892, 7893, 7894, 7895)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.id IN (7024, 7890, 7891, 7892, 7893, 7894, 7895, 7820, 7821, 7822)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- 同步：治理侧 V228 若尚未执行，一并补齐（幂等）
UPDATE sys_menu
SET parent_id = 7220, menu_name = '数据标准体系', menu_type = 1, path = NULL,
    permission = 'hub:gov:quality:standards', sort_order = 1,
    integration_type = 'hub', status = 1, visible = 1
WHERE id = 7221;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7260, 7221, '标准文件管理', 2, '/governance?tab=quality&qSub=standards.file', NULL,
       'hub:gov:quality:standards:file', NULL, 1, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7260 OR permission = 'hub:gov:quality:standards:file');
UPDATE sys_menu SET parent_id = 7221, menu_name = '标准文件管理', menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.file', permission = 'hub:gov:quality:standards:file',
    sort_order = 1, integration_type = 'hub', status = 1, visible = 1 WHERE id = 7260;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7261, 7221, '信息数据元规范', 2, '/governance?tab=quality&qSub=standards.element', NULL,
       'hub:gov:quality:standards:element', NULL, 2, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7261 OR permission = 'hub:gov:quality:standards:element');
UPDATE sys_menu SET parent_id = 7221, menu_name = '信息数据元规范', menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.element', permission = 'hub:gov:quality:standards:element',
    sort_order = 2, integration_type = 'hub', status = 1, visible = 1 WHERE id = 7261;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7262, 7221, '数据编码规范', 2, '/governance?tab=quality&qSub=standards.code', NULL,
       'hub:gov:quality:standards:code', NULL, 3, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7262 OR permission = 'hub:gov:quality:standards:code');
UPDATE sys_menu SET parent_id = 7221, menu_name = '数据编码规范', menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.code', permission = 'hub:gov:quality:standards:code',
    sort_order = 3, integration_type = 'hub', status = 1, visible = 1 WHERE id = 7262;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7263, 7221, '命名规范', 2, '/governance?tab=quality&qSub=standards.naming', NULL,
       'hub:gov:quality:standards:naming', NULL, 4, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7263 OR permission = 'hub:gov:quality:standards:naming');
UPDATE sys_menu SET parent_id = 7221, menu_name = '命名规范', menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.naming', permission = 'hub:gov:quality:standards:naming',
    sort_order = 4, integration_type = 'hub', status = 1, visible = 1 WHERE id = 7263;

UPDATE sys_menu
SET parent_id = 7220, menu_name = '数据质量分析报告', menu_type = 2,
    path = '/governance?tab=quality&qSub=reports', permission = 'hub:gov:quality:reports',
    sort_order = 6, integration_type = 'hub', status = 1, visible = 1
WHERE id = 7226;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
INNER JOIN sys_menu exist ON exist.id = rm.menu_id AND exist.status = 1
  AND (exist.id BETWEEN 7220 AND 7226 OR exist.permission LIKE 'hub:gov:quality:%')
CROSS JOIN sys_menu m
WHERE m.id IN (7221, 7260, 7261, 7262, 7263, 7226)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.id IN (7221, 7260, 7261, 7262, 7263, 7226)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id);

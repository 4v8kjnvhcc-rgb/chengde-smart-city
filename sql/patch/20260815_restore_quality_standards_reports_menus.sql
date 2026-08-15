-- V228: 恢复数据质量管理系统「数据标准体系」「数据质量分析报告」菜单并同步角色授权
-- 对齐标书：标准体系下挂 标准文件/数据元/编码/命名 四叶；报告为独立叶子

-- 1) 数据标准体系：由叶子改为目录
UPDATE sys_menu
SET parent_id = 7220,
    menu_name = '数据标准体系',
    menu_type = 1,
    path = NULL,
    component = NULL,
    permission = 'hub:gov:quality:standards',
    icon = NULL,
    sort_order = 1,
    m_code = NULL,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7221;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7260, 7221, '标准文件管理', 2, '/governance?tab=quality&qSub=standards.file', NULL,
       'hub:gov:quality:standards:file', NULL, 1, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7260 OR permission = 'hub:gov:quality:standards:file');

UPDATE sys_menu
SET parent_id = 7221,
    menu_name = '标准文件管理',
    menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.file',
    permission = 'hub:gov:quality:standards:file',
    sort_order = 1,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7260;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7261, 7221, '信息数据元规范', 2, '/governance?tab=quality&qSub=standards.element', NULL,
       'hub:gov:quality:standards:element', NULL, 2, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7261 OR permission = 'hub:gov:quality:standards:element');

UPDATE sys_menu
SET parent_id = 7221,
    menu_name = '信息数据元规范',
    menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.element',
    permission = 'hub:gov:quality:standards:element',
    sort_order = 2,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7261;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7262, 7221, '数据编码规范', 2, '/governance?tab=quality&qSub=standards.code', NULL,
       'hub:gov:quality:standards:code', NULL, 3, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7262 OR permission = 'hub:gov:quality:standards:code');

UPDATE sys_menu
SET parent_id = 7221,
    menu_name = '数据编码规范',
    menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.code',
    permission = 'hub:gov:quality:standards:code',
    sort_order = 3,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7262;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7263, 7221, '命名规范', 2, '/governance?tab=quality&qSub=standards.naming', NULL,
       'hub:gov:quality:standards:naming', NULL, 4, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7263 OR permission = 'hub:gov:quality:standards:naming');

UPDATE sys_menu
SET parent_id = 7221,
    menu_name = '命名规范',
    menu_type = 2,
    path = '/governance?tab=quality&qSub=standards.naming',
    permission = 'hub:gov:quality:standards:naming',
    sort_order = 4,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7263;

-- 2) 数据质量分析报告恢复
UPDATE sys_menu
SET parent_id = 7220,
    menu_name = '数据质量分析报告',
    menu_type = 2,
    path = '/governance?tab=quality&qSub=reports',
    component = NULL,
    permission = 'hub:gov:quality:reports',
    sort_order = 6,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7226;

-- 3) 质量组排序对齐标书：标准体系(1) → 规则(2) → 监控(4) → 评估(5) → 报告(6)
UPDATE sys_menu SET sort_order = 2, status = 1, visible = 1 WHERE id = 7222;
UPDATE sys_menu SET sort_order = 4, status = 1, visible = 1 WHERE id = 7224;
UPDATE sys_menu SET sort_order = 5, status = 1, visible = 1 WHERE id = 7225;

-- 4) 角色授权：已拥有任一质量叶子的角色，同步勾选标准体系目录/四叶 + 报告；系统管理员全量补齐
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, m.id
FROM sys_role_menu rm
INNER JOIN sys_menu exist ON exist.id = rm.menu_id AND exist.status = 1
  AND (
    exist.id BETWEEN 7220 AND 7226
    OR exist.permission LIKE 'hub:gov:quality:%'
  )
CROSS JOIN sys_menu m
WHERE m.id IN (7221, 7260, 7261, 7262, 7263, 7226)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = m.id
  );

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.id IN (7221, 7260, 7261, 7262, 7263, 7226)
  AND m.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = m.id
  );

-- 5) 标准文件种子分类补齐（可重复执行）
UPDATE gov_standard_item
SET category = COALESCE(NULLIF(category, ''), '数据库设计与运行管理规范'),
    reference_standard = COALESCE(NULLIF(reference_standard, ''), standard_ref),
    business_definition = COALESCE(NULLIF(business_definition, ''), '标书基线：统一业务定义与技术要求，保障数据资产口径一致'),
    publish_status = COALESCE(NULLIF(publish_status, ''), 'PUBLISHED'),
    version_no = COALESCE(version_no, 1)
WHERE item_code = 'STD_FILE_BASE' AND item_type = 'FILE';

INSERT INTO gov_standard_item (item_code, item_name, item_type, category, reference_standard, business_definition, publish_status, version_no, status)
SELECT 'STD_FILE_CODE', '基础信息编码规范', 'FILE', '基础信息编码规范', '项目编码规范',
       '统一平台基础库各类基础信息编码，特别是无国标/行标的编码项', 'PUBLISHED', 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_standard_item WHERE item_code = 'STD_FILE_CODE');

INSERT INTO gov_standard_item (item_code, item_name, item_type, category, reference_standard, business_definition, publish_status, version_no, status)
SELECT 'STD_FILE_API', '信息接口管理规范', 'FILE', '信息接口管理规范', '接口规范',
       '指导数据服务接口的定义、结构与数据格式', 'PUBLISHED', 1, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM gov_standard_item WHERE item_code = 'STD_FILE_API');

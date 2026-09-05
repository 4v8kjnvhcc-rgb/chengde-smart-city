-- V255: 归集侧栏「部门数据管理」（规范设计与指标与目录之间）
-- 子菜单：模板管理、上传记录；与本地文件上传共用模板/记录数据，按机构隔离
-- 授权：SYSTEM_ADMIN、DEPT_ADMIN

-- 插入顺序：规范设计(2) → 部门数据管理(3) → 指标与目录(4) → 质量(5) → 资产(6)
UPDATE sys_menu SET sort_order = 4 WHERE id = 7023;
UPDATE sys_menu SET sort_order = 5 WHERE id = 7024;
UPDATE sys_menu SET sort_order = 6 WHERE id = 7025;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7900, 7020, '部门数据管理', 1, NULL, NULL, 'hub:ingestion:collect:dept-data', NULL, 3, 'DDM', 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7900 OR permission = 'hub:ingestion:collect:dept-data');

UPDATE sys_menu
SET parent_id = 7020,
    menu_name = '部门数据管理',
    menu_type = 1,
    path = NULL,
    component = NULL,
    permission = 'hub:ingestion:collect:dept-data',
    sort_order = 3,
    m_code = 'DDM',
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7900;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7901, 7900, '模板管理', 2,
       '/exchange/ingestion?system=collect&module=dept-data.templates', NULL,
       'hub:ingestion:collect:dept-data:templates', NULL, 1, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7901 OR permission = 'hub:ingestion:collect:dept-data:templates');

UPDATE sys_menu
SET parent_id = 7900,
    menu_name = '模板管理',
    menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=dept-data.templates',
    permission = 'hub:ingestion:collect:dept-data:templates',
    sort_order = 1,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7901;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7902, 7900, '上传记录', 2,
       '/exchange/ingestion?system=collect&module=dept-data.records', NULL,
       'hub:ingestion:collect:dept-data:records', NULL, 2, NULL, 'hub', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7902 OR permission = 'hub:ingestion:collect:dept-data:records');

UPDATE sys_menu
SET parent_id = 7900,
    menu_name = '上传记录',
    menu_type = 2,
    path = '/exchange/ingestion?system=collect&module=dept-data.records',
    permission = 'hub:ingestion:collect:dept-data:records',
    sort_order = 2,
    integration_type = 'hub',
    status = 1,
    visible = 1
WHERE id = 7902;

-- SYSTEM_ADMIN / DEPT_ADMIN 授权父+子菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('SYSTEM_ADMIN', 'DEPT_ADMIN')
  AND m.id IN (7900, 7901, 7902)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 确保部门管理员能进入归集「数据资源采集汇聚」父节点
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'DEPT_ADMIN'
  AND m.id = 7020
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

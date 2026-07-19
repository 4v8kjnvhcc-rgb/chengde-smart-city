-- 系统管理 IA 梳理（A+X）：三组目录、访问控制入系统、供需/考核与调度/ETL 迁出、侧栏去重

-- 1) 系统管理下三个分组目录
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6400, 19, '身份与权限', 1, NULL, NULL, NULL, NULL, 10, NULL, 'self', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6400);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6401, 19, '安全与合规', 1, NULL, NULL, NULL, NULL, 20, NULL, 'self', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6401);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6402, 19, '平台运维', 1, NULL, NULL, NULL, NULL, 30, NULL, 'self', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6402);

-- 2) 访问控制（系统侧正式入口）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6403, 6400, '访问控制', 2, '/system/access', 'exchange/ingestion/register/AccessControlView', 'system:access:view', NULL, 12, 'M048', 'self', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6403 OR path = '/system/access');

-- 3) 挂载到分组
UPDATE sys_menu SET parent_id = 6400, sort_order = 11
WHERE path = '/system/uum' AND status = 1;

UPDATE sys_menu SET parent_id = 6401, sort_order = 21 WHERE id = 28;
UPDATE sys_menu SET parent_id = 6401, sort_order = 22 WHERE id = 29;

UPDATE sys_menu SET parent_id = 6402, sort_order = 31 WHERE id = 27;
UPDATE sys_menu SET parent_id = 6402, sort_order = 32 WHERE id = 6001;
UPDATE sys_menu SET parent_id = 6402, menu_name = '标签库', sort_order = 33 WHERE id = 6300;

-- 4) 侧栏去重：用户/角色/机构保留路由与权限，不在侧栏展示
UPDATE sys_menu SET status = 0, parent_id = 6400, sort_order = 15 WHERE id IN (20, 25, 26);

-- 5) 供需/考核配置迁到交换「应用平台」；系统下旧目录隐藏
UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application/supply-config',
    component = 'system/SupplyConfigView',
    sort_order = 10
WHERE id = 6102;

UPDATE sys_menu
SET parent_id = 6,
    path = '/exchange/application/assessment-config',
    component = 'system/AssessmentConfigView',
    sort_order = 11
WHERE id = 6103;

UPDATE sys_menu SET status = 0 WHERE id IN (6100, 6101);

-- 6) 一级平台：集成运维；调度/ETL 迁入
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6500, 1, '集成运维', 1, '/integration', NULL, NULL, 'Connection', 17, NULL, 'self', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6500 OR path = '/integration');

UPDATE sys_menu SET parent_id = 6500, sort_order = 1 WHERE id = 30;
UPDATE sys_menu SET parent_id = 6500, sort_order = 2 WHERE id = 31;

-- 7) M139 纠偏：通用支撑让出 M139，UUM 独占
UPDATE sys_menu SET m_code = 'M139A' WHERE id = 13 AND path = '/analytics/support';
UPDATE sys_menu SET m_code = 'M139' WHERE path = '/system/uum';

-- 8) 赋权：目录与新菜单给系统管理员、机构管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.id
FROM (SELECT 1 AS role_id UNION ALL SELECT 2) r
CROSS JOIN (
    SELECT id FROM sys_menu WHERE id IN (6400, 6401, 6402, 6403, 6500)
) m
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = m.id
);

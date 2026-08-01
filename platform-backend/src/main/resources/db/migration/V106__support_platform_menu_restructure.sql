-- V106: 系统管理 + 通用支撑 整合为「通用支撑平台」新 IA
-- 门户侧栏以 /analytics/support Hub 为准；原 /system 分组侧栏隐藏（保留 status 供 RBAC）
-- 子菜单 ID 使用 7700+，避开智能 BI 已占用的 7510～7523

-- 1) 隐藏原系统管理侧栏分组与可见入口（权限菜单仍 status=1）
UPDATE sys_menu SET visible = 0
WHERE id IN (6400, 6401, 6402, 6403, 6410, 19, 20, 25, 26, 27, 28, 29, 6001, 6300)
   OR parent_id IN (6400, 6401, 6402)
   OR id BETWEEN 7600 AND 7611
   OR path IN (
        '/system/uum', '/system/users', '/system/roles', '/system/orgs', '/system/menus',
        '/system/portal-links', '/system/tags', '/system/audit', '/system/maintenance',
        '/system/security', '/system/access'
   )
   OR path LIKE '/system/uum%';

-- 2) 重建通用支撑 Hub 目录（挂在 id=13）
UPDATE sys_menu
SET menu_name = '用户中心', menu_type = 1, path = NULL, component = NULL,
    permission = 'hub:analytics:support:users', sort_order = 1, m_code = 'M139',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7500;

UPDATE sys_menu
SET menu_name = '应用中心', menu_type = 1, path = NULL, component = NULL,
    permission = 'hub:analytics:support:apps', sort_order = 2, m_code = 'M140',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7501;

UPDATE sys_menu
SET menu_name = '认证中心', menu_type = 2, path = '/analytics/support?tab=auth', component = NULL,
    permission = 'hub:analytics:support:auth', sort_order = 3, m_code = 'M141',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7502;

UPDATE sys_menu
SET menu_name = '服务中心', menu_type = 2, path = '/analytics/support?tab=services', component = NULL,
    permission = 'hub:analytics:support:services', sort_order = 4, m_code = 'M142',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7503;

UPDATE sys_menu
SET menu_name = '系统管理', menu_type = 1, path = NULL, component = NULL,
    permission = 'hub:analytics:support:config', sort_order = 5, m_code = 'M143',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7504;

UPDATE sys_menu
SET menu_name = '日志审计', menu_type = 1, path = NULL, component = NULL,
    permission = 'hub:analytics:support:audit', sort_order = 6, m_code = 'M144',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7505;

UPDATE sys_menu
SET menu_name = '其他', menu_type = 1, path = NULL, component = NULL,
    permission = 'hub:analytics:support:other', sort_order = 7, m_code = 'M145',
    integration_type = 'hub', status = 1, visible = 0
WHERE id = 7506;

-- 用户中心子项（7700+）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7700, 7500, '组织管理', 2, '/analytics/support?tab=users.org', NULL, 'hub:analytics:support:users:org', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7700);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7701, 7500, '用户管理', 2, '/analytics/support?tab=users.user', NULL, 'hub:analytics:support:users:user', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7701);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7702, 7500, '角色管理', 2, '/analytics/support?tab=users.role', NULL, 'hub:analytics:support:users:role', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7702);

-- 应用中心子项
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7710, 7501, '应用管理', 2, '/analytics/support?tab=apps.manage', NULL, 'hub:analytics:support:apps:manage', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7710);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7711, 7501, '系统对接', 2, '/analytics/support?tab=apps.integration', NULL, 'hub:analytics:support:apps:integration', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7711);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7712, 7501, '门户配置', 2, '/analytics/support?tab=apps.portal', NULL, 'hub:analytics:support:apps:portal', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7712);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7713, 7501, '门户外链管理', 2, '/analytics/support?tab=apps.links', NULL, 'hub:analytics:support:apps:links', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7713);

-- 系统管理子项（7720+；若上次误用 7530 已存在则跳过）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7720, 7504, '菜单管理', 2, '/analytics/support?tab=sys.menus', NULL, 'hub:analytics:support:sys:menus', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7720 OR (parent_id = 7504 AND menu_name = '菜单管理'));

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7721, 7504, '字典管理', 2, '/analytics/support?tab=sys.dict', NULL, 'hub:analytics:support:sys:dict', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7721 OR (parent_id = 7504 AND menu_name = '字典管理'));

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7722, 7504, '系统配置', 1, NULL, NULL, 'hub:analytics:support:sys:cfg', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7722 OR (parent_id = 7504 AND menu_name = '系统配置'));

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7723, cfg.id, '基础信息', 2, '/analytics/support?tab=sys.cfg.appearance', NULL, 'hub:analytics:support:sys:appearance', NULL, 1, NULL, 'hub', 1, 0
FROM (SELECT id FROM sys_menu WHERE parent_id = 7504 AND menu_name = '系统配置' ORDER BY id LIMIT 1) cfg
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7723 OR path = '/analytics/support?tab=sys.cfg.appearance');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7724, cfg.id, '系统邮箱', 2, '/analytics/support?tab=sys.cfg.mail', NULL, 'hub:analytics:support:sys:mail', NULL, 2, NULL, 'hub', 1, 0
FROM (SELECT id FROM sys_menu WHERE parent_id = 7504 AND menu_name = '系统配置' ORDER BY id LIMIT 1) cfg
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7724 OR path = '/analytics/support?tab=sys.cfg.mail');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7725, 7504, '标签库', 2, '/analytics/support?tab=sys.tags', NULL, 'hub:analytics:support:sys:tags', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7725 OR (parent_id = 7504 AND menu_name = '标签库'));

-- 日志审计子项
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7730, 7505, '日志审计', 2, '/analytics/support?tab=audit.log', NULL, 'hub:analytics:support:audit:log', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7730 OR path = '/analytics/support?tab=audit.log');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7731, 7505, '访问控制', 2, '/analytics/support?tab=audit.access', NULL, 'hub:analytics:support:audit:access', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7731 OR path = '/analytics/support?tab=audit.access');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7732, 7505, '等保安全', 2, '/analytics/support?tab=audit.security', NULL, 'hub:analytics:support:audit:security', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7732 OR path = '/analytics/support?tab=audit.security');

-- 其他
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7740, 7506, '角色菜单权限', 2, '/analytics/support?tab=other.roleMenus', NULL, 'hub:analytics:support:other:roleMenus', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7740 OR path = '/analytics/support?tab=other.roleMenus');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7741, 7506, '对接探测', 2, '/analytics/support?tab=other.probe', NULL, 'hub:analytics:support:other:probe', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7741 OR path = '/analytics/support?tab=other.probe');

-- 入口：通用支撑平台默认落到组织管理
UPDATE sys_menu
SET path = '/analytics/support?tab=users.org', menu_name = '通用支撑平台'
WHERE id = 13 OR path = '/analytics/support';

-- 赋权系统管理员
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE (
    m.id IN (
        7500, 7501, 7502, 7503, 7504, 7505, 7506,
        7700, 7701, 7702,
        7710, 7711, 7712, 7713,
        7720, 7721, 7722, 7723, 7724, 7725,
        7730, 7731, 7732,
        7740, 7741
    )
    OR (m.id BETWEEN 7530 AND 7551 AND m.parent_id IN (7504, 7505, 7506, 7532))
)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

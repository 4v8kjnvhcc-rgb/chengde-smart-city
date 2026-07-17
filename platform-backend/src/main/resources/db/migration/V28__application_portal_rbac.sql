-- 应用平台改造：门户 RBAC（m_code ≤16；按钮权限以 permission 为准，m_code 用短码）

INSERT INTO sys_role (id, role_code, role_name, role_type, description) VALUES
(5, 'RESOURCE_ADMIN', '资源管理处', 2, '数据资源管理、考核评估、统计分析'),
(6, 'LEADER', '领导角色', 2, '决策驾驶舱查看权限') AS new_role
ON DUPLICATE KEY UPDATE
  role_name = new_role.role_name,
  description = new_role.description;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6201, 1, '考核评估查看', 3, '', NULL, 'portal:assessment:view', NULL, 901, 'P-ASSESS', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'portal:assessment:view' OR id = 6201);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6202, 1, '数据需求创建', 3, '', NULL, 'portal:supply:create', NULL, 902, 'P-SUP-C', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'portal:supply:create' OR id = 6202);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6203, 1, '数据需求审批', 3, '', NULL, 'portal:supply:approve', NULL, 903, 'P-SUP-A', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'portal:supply:approve' OR id = 6203);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6204, 1, '资源订阅创建', 3, '', NULL, 'portal:subscription:create', NULL, 904, 'P-SUB-C', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'portal:subscription:create' OR id = 6204);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6205, 1, '资源订阅审批', 3, '', NULL, 'portal:subscription:review', NULL, 905, 'P-SUB-R', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'portal:subscription:review' OR id = 6205);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6206, 1, '决策驾驶舱查看', 3, '', NULL, 'analytics:cockpit:view', NULL, 906, 'P-COCKPIT', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'analytics:cockpit:view' OR id = 6206);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 6207, 1, '统计分析查看', 3, '', NULL, 'analytics:stats:view', NULL, 907, 'P-STATS', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = 'analytics:stats:view' OR id = 6207);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.permission IN (
  'portal:assessment:view', 'portal:supply:create', 'portal:supply:approve',
  'portal:subscription:create', 'portal:subscription:review',
  'analytics:cockpit:view', 'analytics:stats:view'
)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, m.id FROM sys_menu m
WHERE m.permission IN (
  'portal:assessment:view', 'portal:supply:approve',
  'portal:subscription:review', 'analytics:stats:view',
  'portal:supply:create', 'portal:subscription:create'
)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 5 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, m.id FROM sys_menu m
WHERE m.permission IN (
  'analytics:cockpit:view', 'analytics:stats:view',
  'portal:subscription:create', 'portal:supply:create'
)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 6 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, m.id FROM sys_menu m
WHERE m.permission IN (
  'portal:supply:create', 'portal:supply:approve',
  'portal:subscription:create', 'portal:subscription:review',
  'analytics:stats:view', 'portal:assessment:view'
)
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 2 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 3, m.id FROM sys_menu m
WHERE m.permission IN ('portal:supply:create', 'portal:subscription:create')
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 3 AND rm.menu_id = m.id);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, 5 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1 AND role_id = 5);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_user_role WHERE user_id = 1 AND role_id = 6);

UPDATE sys_menu
SET path = '/exchange/application',
    component = 'exchange/application/ApplicationHubView',
    menu_name = '应用分析门户(已并入应用平台)'
WHERE path IN ('/exchange/portal', '/exchange/analysis-portal') OR id = 7;

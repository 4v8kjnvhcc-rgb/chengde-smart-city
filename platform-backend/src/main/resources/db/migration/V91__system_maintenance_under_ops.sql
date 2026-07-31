-- V91：系统维护管理独立入口挂到「平台运维」；UUM Hub 内原「系统管理/维护」子菜单隐藏

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status)
SELECT 6410, 6402, '系统维护管理', 2, '/system/maintenance', 'system/maintenance/SystemMaintenanceHubView',
       'system:security:config', NULL, 34, 'M049', 'self', 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6410 OR path = '/system/maintenance');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, 6410
FROM (SELECT 1 AS role_id UNION ALL SELECT 2) r
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 6410)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.role_id AND rm.menu_id = 6410);

-- UUM Hub 内不再展示「系统维护管理」子项（能力已迁平台运维）
UPDATE sys_menu SET status = 0 WHERE id = 7604 AND path = '/system/uum?tab=config';

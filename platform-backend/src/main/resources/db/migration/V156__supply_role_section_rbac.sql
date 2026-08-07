-- V156: 供需对接三角色权限对齐
-- 部门管理员：portal:supply:create（需求管理/确认/供给/清单）
-- 平台管理员：portal:supply:approve + system:exchange:supply-config（首页/分析/督办/供给/清单/系统管理）
-- 超级管理员：全部（已有全量权限）

-- 1) DEPT_ADMIN：去掉审批权，仅保留创建
DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id AND r.role_code = 'DEPT_ADMIN'
INNER JOIN sys_menu m ON m.id = rm.menu_id AND m.permission = 'portal:supply:approve';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'DEPT_ADMIN'
  AND m.permission = 'portal:supply:create'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 2) PLATFORM_ADMIN：审批 + 供需配置；去掉创建（平台不做需求填报/确认）
DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id AND r.role_code = 'PLATFORM_ADMIN'
INNER JOIN sys_menu m ON m.id = rm.menu_id AND m.permission = 'portal:supply:create';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'PLATFORM_ADMIN'
  AND m.permission IN ('portal:supply:approve', 'system:exchange:supply-config')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 3) SYSTEM_ADMIN：确保 create / approve / supply-config 均具备
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'SYSTEM_ADMIN'
  AND m.permission IN ('portal:supply:create', 'portal:supply:approve', 'system:exchange:supply-config')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- 4) 资源管理处（RESOURCE_ADMIN）按平台口径：保留审批，去掉创建
DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id AND r.role_code = 'RESOURCE_ADMIN'
INNER JOIN sys_menu m ON m.id = rm.menu_id AND m.permission = 'portal:supply:create';

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'RESOURCE_ADMIN'
  AND m.permission IN ('portal:supply:approve', 'system:exchange:supply-config')
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id
  );

-- V128: 修复「项目/系统信息管理」菜单未写入
-- 原因：V124（原 V118）误用 id=7020，该 ID 在 V80 已占用为「数据资源采集汇聚」目录，INSERT 被 WHERE NOT EXISTS 跳过

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7026, 7000, '项目/系统信息管理', 2,
       '/exchange/ingestion?system=register&module=project-system-mgmt', NULL,
       'hub:ingestion:register:project-system-mgmt', NULL, 6, NULL, 'hub', 1, 1
WHERE NOT EXISTS (
  SELECT 1 FROM sys_menu
  WHERE id = 7026 OR permission = 'hub:ingestion:register:project-system-mgmt'
);

-- 平台管理员、系统管理员默认可见
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code IN ('PLATFORM_ADMIN', 'SYSTEM_ADMIN')
  AND m.permission = 'hub:ingestion:register:project-system-mgmt'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id);

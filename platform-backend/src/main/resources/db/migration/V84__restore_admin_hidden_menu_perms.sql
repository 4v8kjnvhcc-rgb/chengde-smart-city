-- 补全系统管理员权限：侧栏隐藏但仍需权限码的菜单（用户/角色/机构等 status=0）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE (m.status = 1 OR (m.permission IS NOT NULL AND TRIM(m.permission) <> ''))
  AND IFNULL(m.integration_type, '') <> 'catalog'
  AND IFNULL(m.menu_name, '') NOT LIKE '%D05%'
  AND IFNULL(m.menu_name, '') NOT LIKE '%已并入%'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

-- MS1 收口：机构 CRUD 权限 + MS2 演示菜单改为 self 入口

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 32, 26, '机构新增', 3, NULL, NULL, 'system:org:add', NULL, 32, 'M048', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 32);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 33, 26, '机构编辑', 3, NULL, NULL, 'system:org:edit', NULL, 33, 'M048', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 33);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type)
SELECT 34, 26, '机构删除', 3, NULL, NULL, 'system:org:delete', NULL, 34, 'M048', 'self'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 34);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id FROM sys_menu m
WHERE m.id IN (32, 33, 34)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 端到端演示入口：归集/应用/ESB/ETL 改为本系统页面
UPDATE sys_menu SET integration_type = 'self', component = 'exchange/IngestionView' WHERE id = 4;
UPDATE sys_menu SET integration_type = 'self', component = 'exchange/EsbView' WHERE id = 5;
UPDATE sys_menu SET integration_type = 'self', component = 'exchange/ApplicationView' WHERE id = 6;
UPDATE sys_menu SET integration_type = 'self', component = 'exchange/CatalogPortalView' WHERE path = '/exchange/analysis-portal' OR id = 7;
UPDATE sys_menu SET integration_type = 'self', component = 'integration/KettleView' WHERE id = 31;

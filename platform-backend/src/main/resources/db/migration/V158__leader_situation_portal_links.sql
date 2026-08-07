-- 领导决策门户 · 八态势跳转地址（门户配置，默认空）
-- 在统一用户管理 → 门户配置中维护 url；驾驶舱整卡点击跳转

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 143, 14, '领导决策八态势', 'sub_platform', 3, NULL, NULL, 'route', NULL, '八态势模块跳转配置（默认地址为空）', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 143);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1431, 143, '人口态势', 'system', 1, NULL, NULL, 'new_tab', NULL, 'SITUATION:POPULATION', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1431);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1432, 143, '法人态势', 'system', 2, NULL, NULL, 'new_tab', NULL, 'SITUATION:LEGAL', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1432);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1433, 143, '法人行业态势', 'system', 3, NULL, NULL, 'new_tab', NULL, 'SITUATION:LEGAL_INDUSTRY', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1433);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1434, 143, '产业发展分布', 'system', 4, NULL, NULL, 'new_tab', NULL, 'SITUATION:INDUSTRY', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1434);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1435, 143, '经济运行态势', 'system', 5, NULL, NULL, 'new_tab', NULL, 'SITUATION:ECONOMY', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1435);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1436, 143, '社会治理态势', 'system', 6, NULL, NULL, 'new_tab', NULL, 'SITUATION:SOCIAL', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1436);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1437, 143, '城市管理态势', 'system', 7, NULL, NULL, 'new_tab', NULL, 'SITUATION:CITY', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1437);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 1438, 143, '公共安全态势', 'system', 8, NULL, NULL, 'new_tab', NULL, 'SITUATION:SAFETY', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 1438);

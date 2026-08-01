-- V107: 门户 IA 对齐图四
-- 1) 集成运维迁入通用支撑；新增任务管理
-- 2) 一级「系统管理」更名为「平台管理」
-- 3) 补齐 portal_nav_node（业务支撑系统、任务管理、集成运维；服务总线暂指统一用户管理）

-- ── 1. 一级平台：系统管理 → 平台管理 ───────────────────
UPDATE sys_menu
SET menu_name = '平台管理'
WHERE (path = '/system' OR id = 19)
  AND menu_name = '系统管理';

-- ── 2. 隐藏门户一级「集成运维」卡片（功能迁入通用支撑）──
UPDATE sys_menu
SET visible = 0
WHERE id = 6500 OR path = '/integration';

-- ── 3. 通用支撑 Hub：任务管理 + 集成运维 ───────────────
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7507, 13, '任务管理', 2, '/analytics/support?tab=tasks', NULL,
       'hub:analytics:support:tasks', NULL, 8, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7507 OR permission = 'hub:analytics:support:tasks');

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7508, 13, '集成运维', 2, '/analytics/support?tab=ops.kettle', NULL,
       'hub:analytics:support:ops', NULL, 9, 'M215', 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7508 OR permission = 'hub:analytics:support:ops');

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7507, 7508)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- 旧集成运维入口改为跳转通用支撑（菜单 path 更新，路由层另有 redirect）
UPDATE sys_menu
SET path = '/analytics/support?tab=tasks', parent_id = 13, visible = 0
WHERE id = 30;

UPDATE sys_menu
SET path = '/analytics/support?tab=ops.kettle', parent_id = 13, visible = 0
WHERE id = 31;

-- ── 4. portal_nav_node：服务总线暂指统一用户管理 ────────
UPDATE portal_nav_node
SET url = '/analytics/support',
    menu_path = '/analytics/support',
    remark = '外接系统占位：暂跳统一用户管理，后期在菜单管理配置'
WHERE id = 12 AND node_type = 'sub_platform';

-- 通用支撑：统一用户 → /analytics/support
UPDATE portal_nav_node
SET url = '/analytics/support',
    menu_path = '/analytics/support'
WHERE id = 311;

-- 补：任务管理、集成运维
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 313, 31, '任务管理', 'system', 3,
       '/analytics/support?tab=tasks', '/analytics/support?tab=tasks', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 313);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 314, 31, '集成运维', 'system', 4,
       '/analytics/support?tab=ops.kettle', '/analytics/support?tab=ops.kettle', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 314);

-- 业务支撑平台下四个系统
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 321, 32, '人口大数据支撑系统', 'system', 1,
       '/analytics/population', '/analytics/population', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 321);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 322, 32, '法人大数据支撑系统', 'system', 2,
       '/analytics/legal-entity', '/analytics/legal-entity', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 322);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 323, 32, '宏观经济及工业运行大数据支撑系统', 'system', 3,
       '/analytics/macro', '/analytics/macro', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 323);

INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, theme_key, remark, status)
SELECT 324, 32, '重点领域示范应用支撑系统', 'system', 4,
       '/analytics/key-domains', '/analytics/key-domains', 'route', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 324);

-- 业务支撑子平台本身改为分组（系统可点，子平台可不单独跳）
UPDATE portal_nav_node
SET url = NULL, menu_path = NULL, remark = NULL
WHERE id = 32 AND node_type = 'sub_platform';

-- 通用支撑子平台可进入默认页
UPDATE portal_nav_node
SET url = '/analytics/support',
    menu_path = '/analytics/support'
WHERE id = 31 AND node_type = 'sub_platform';

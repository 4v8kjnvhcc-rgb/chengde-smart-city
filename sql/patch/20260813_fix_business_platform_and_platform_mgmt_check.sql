-- V210: 业务功能平台 / 平台管理可勾选；补齐「承德市高新区人口信息库」配置与门户入口
-- 1) 无下级的目录（平台管理）改为可授权菜单项，避免角色树勾选不回显
-- 2) 业务功能平台下挂人口信息库菜单，并与 portal_nav menu_path 对齐

-- ========== A. 平台管理：无下级快捷入口，按叶子菜单授权 ==========
UPDATE sys_menu
SET parent_id = 1,
    menu_name = '平台管理',
    menu_type = 2,
    path = '/system',
    component = NULL,
    permission = 'hub:system:platform',
    icon = NULL,
    sort_order = 90,
    integration_type = 'self',
    status = 1,
    visible = 1
WHERE id = 19;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 19, 1, '平台管理', 2, '/system', NULL, 'hub:system:platform', NULL, 90, NULL, 'self', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 19);

-- 子树继续停用
UPDATE sys_menu
SET status = 0, visible = 0
WHERE id IN (27, 6400, 6401, 6402, 6403, 6500, 6001, 6002, 6003, 6004, 6005)
   OR parent_id IN (19, 6400, 6401, 6402)
   OR (IFNULL(path, '') LIKE '/system/%' AND path <> '/system');

-- ========== B. 业务功能平台 + 人口信息库 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 6000, 1, '业务功能平台', 1, '/business', NULL, 'hub:business:platform', 'Grid', 18, NULL, 'self', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6000 OR path = '/business');

UPDATE sys_menu
SET parent_id = 1,
    menu_name = '业务功能平台',
    menu_type = 1,
    path = '/business',
    permission = 'hub:business:platform',
    icon = 'Grid',
    sort_order = 18,
    integration_type = 'self',
    status = 1,
    visible = 1
WHERE id = 6000;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 6010, 6000, '承德市高新区人口信息库', 2, '/business/gaoxin-pop-lib', NULL,
       'hub:business:gaoxin-pop-lib', NULL, 1, NULL, 'self', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 6010 OR path = '/business/gaoxin-pop-lib');

UPDATE sys_menu
SET parent_id = 6000,
    menu_name = '承德市高新区人口信息库',
    menu_type = 2,
    path = '/business/gaoxin-pop-lib',
    permission = 'hub:business:gaoxin-pop-lib',
    sort_order = 1,
    integration_type = 'self',
    status = 1,
    visible = 0
WHERE id = 6010;

-- ========== C. 门户导航：业务功能平台下挂人口库（对齐 sys_menu path） ==========
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, sso_mode, theme_key, remark, status)
SELECT 41, 4, '承德市高新区人口信息库', 'sub_platform', 1,
       'http://10.10.10.11/', '/business/gaoxin-pop-lib', 'new_tab', 'none', NULL,
       '业务功能外链；请在门户配置中改为实际地址', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 41)
  AND NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE parent_id = 4 AND name = '承德市高新区人口信息库');

UPDATE portal_nav_node
SET name = '承德市高新区人口信息库',
    parent_id = 4,
    node_type = 'sub_platform',
    sort_order = 1,
    menu_path = '/business/gaoxin-pop-lib',
    open_mode = CASE WHEN IFNULL(open_mode, '') = '' THEN 'new_tab' ELSE open_mode END,
    status = 1,
    remark = CASE
      WHEN IFNULL(remark, '') = '' THEN '业务功能外链；地址可在门户配置中维护'
      ELSE remark
    END
WHERE id = 41
   OR (parent_id = 4 AND name LIKE '%人口信息库%');

-- 若已有人口库节点但无可用 url，给占位（不覆盖已配置外链）
UPDATE portal_nav_node
SET url = 'http://10.10.10.11/',
    open_mode = 'new_tab'
WHERE (id = 41 OR (parent_id = 4 AND name LIKE '%人口信息库%'))
  AND (url IS NULL OR TRIM(url) = '');

UPDATE portal_nav_node
SET status = 1,
    theme_key = '/business'
WHERE id = 4;

-- ========== D. 授权 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (19, 6000, 6010)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

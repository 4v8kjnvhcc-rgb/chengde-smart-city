-- V214: 去掉配置树多余「日志审计」；门户一级补「平台管理」卡片
-- 1) V210 误将旧 UUM 节点 7605 以 path=NULL 重新启用，父级已停用 → 角色树漂成顶级「日志审计 [目录]」
-- 2) 门户 portal_nav 增加平台管理一级卡（勾选 sys_menu /system 后展示，点击进统一用户）

-- ========== A. 停用旧 /system/uum 日志审计重复树 ==========
UPDATE sys_menu
SET status = 0, visible = 0
WHERE id BETWEEN 7600 AND 7611
   OR id IN (7630, 7631, 7632, 7633);

-- 真正的日志审计保留在统一用户管理系统下（id=7505）
UPDATE sys_menu
SET parent_id = 13,
    menu_name = '日志审计',
    menu_type = 1,
    status = 1,
    visible = 0,
    sort_order = 8
WHERE id = 7505;

-- ========== B. 门户：平台管理一级卡片 ==========
INSERT INTO portal_nav_node (id, parent_id, name, node_type, sort_order, url, menu_path, open_mode, sso_mode, theme_key, remark, status)
SELECT 5, 0, '平台管理', 'platform', 5,
       '/analytics/support', '/system', 'route', 'none', '/system',
       '一级目录快捷入口：点击进入统一用户管理系统', 1
WHERE NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE id = 5)
  AND NOT EXISTS (SELECT 1 FROM portal_nav_node WHERE parent_id = 0 AND name = '平台管理');

UPDATE portal_nav_node
SET name = '平台管理',
    parent_id = 0,
    node_type = 'platform',
    sort_order = 5,
    url = '/analytics/support',
    menu_path = '/system',
    open_mode = 'route',
    theme_key = '/system',
    status = 1,
    remark = '一级目录快捷入口：点击进入统一用户管理系统'
WHERE id = 5
   OR (parent_id = 0 AND name = '平台管理');

-- 确保平台管理菜单目录可用（供 portal 按 menu_path 授权过滤）
UPDATE sys_menu
SET parent_id = 1,
    menu_name = '平台管理',
    menu_type = 1,
    path = '/system',
    permission = 'hub:system:platform',
    status = 1,
    visible = 1,
    sort_order = 90
WHERE id = 19;

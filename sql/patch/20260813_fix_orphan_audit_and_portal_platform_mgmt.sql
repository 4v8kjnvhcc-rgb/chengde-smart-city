-- 手工补丁（对应 Flyway V214）
-- 目标库：smart_city
-- 可重复执行
-- 1) 去掉角色配置树顶级多余「日志审计」（旧 7605 树）
-- 2) 门户增加「平台管理」一级卡

UPDATE sys_menu
SET status = 0, visible = 0
WHERE id BETWEEN 7600 AND 7611
   OR id IN (7630, 7631, 7632, 7633);

UPDATE sys_menu
SET parent_id = 13,
    menu_name = '日志审计',
    menu_type = 1,
    status = 1,
    visible = 0,
    sort_order = 8
WHERE id = 7505;

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

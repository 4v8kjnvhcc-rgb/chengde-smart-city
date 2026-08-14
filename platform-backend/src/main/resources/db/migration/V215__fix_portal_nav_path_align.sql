-- V215: 门户导航 path 与 sys_menu 对齐
-- 根因：现场 portal_nav 被改成 /bigdata-web/exchange/... 且 menu_path 为空；
-- 首页 enabled-tree 按 menu_path/url 与用户菜单 path 匹配，匹配失败则 prune，
-- 表现为「角色菜单全勾了，门户却只剩应用平台」。

-- 大数据归集
UPDATE portal_nav_node
SET url = '/exchange/ingestion', menu_path = '/exchange/ingestion'
WHERE id = 11;

UPDATE portal_nav_node
SET url = '/exchange/ingestion?system=register',
    menu_path = '/exchange/ingestion?system=register'
WHERE id = 111;

UPDATE portal_nav_node
SET url = '/exchange/ingestion?system=collect',
    menu_path = '/exchange/ingestion?system=collect'
WHERE id = 112;

-- 服务总线：保留外链，补 menu_path 供权限匹配
UPDATE portal_nav_node
SET menu_path = '/exchange/esb'
WHERE id = 12
  AND (menu_path IS NULL OR menu_path = '' OR menu_path LIKE '/bigdata-web%');

-- 应用平台（子项 131~134 已在 V208 对齐）
UPDATE portal_nav_node
SET url = '/exchange/application', menu_path = '/exchange/application'
WHERE id = 13;

-- 应用分析门户
UPDATE portal_nav_node
SET url = '/exchange/analysis-portal', menu_path = '/exchange/analysis-portal'
WHERE id = 14;

UPDATE portal_nav_node
SET url = '/exchange/analysis-portal/dept',
    menu_path = '/exchange/analysis-portal/dept'
WHERE id = 141;

UPDATE portal_nav_node
SET url = '/exchange/analysis-portal/leader',
    menu_path = '/exchange/analysis-portal/leader'
WHERE id = 143
  AND status = 1;

-- 兜底：剥掉仍残留的 /bigdata-web 前缀
UPDATE portal_nav_node
SET url = SUBSTRING(url, CHAR_LENGTH('/bigdata-web') + 1)
WHERE url LIKE '/bigdata-web/%';

UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path LIKE '/bigdata-web/%';

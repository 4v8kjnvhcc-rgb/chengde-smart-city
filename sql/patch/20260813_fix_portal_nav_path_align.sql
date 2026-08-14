-- 手工补丁（生产可重复执行）：门户导航 path 与 sys_menu 对齐
-- 目标库：smart_city @ 10.10.10.56
-- 对应 Flyway：V215__fix_portal_nav_path_align.sql
-- 执行后无需重建容器；用户重新登录或刷新首页即可（清门户导航缓存）

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

UPDATE portal_nav_node
SET menu_path = '/exchange/esb'
WHERE id = 12
  AND (menu_path IS NULL OR menu_path = '' OR menu_path LIKE '/bigdata-web%');

UPDATE portal_nav_node
SET url = '/exchange/application', menu_path = '/exchange/application'
WHERE id = 13;

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

UPDATE portal_nav_node
SET url = SUBSTRING(url, CHAR_LENGTH('/bigdata-web') + 1)
WHERE url LIKE '/bigdata-web/%';

UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path LIKE '/bigdata-web/%';

-- 验收
SELECT id, name, menu_path, url
FROM portal_nav_node
WHERE id IN (11, 12, 13, 14, 111, 112, 141, 143)
ORDER BY id;

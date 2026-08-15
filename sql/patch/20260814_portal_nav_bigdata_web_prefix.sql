-- 目标库：smart_city（10.10.10.56:13306）
-- 门户 portal_nav_node：站内 url 补 /bigdata-web；menu_path 保持无此前缀
-- 可重复执行（幂等）

UPDATE portal_nav_node
SET url = REPLACE(url, '/exchange/exchange/', '/exchange/'),
    menu_path = REPLACE(IFNULL(menu_path, ''), '/exchange/exchange/', '/exchange/')
WHERE IFNULL(url, '') LIKE '%/exchange/exchange/%'
   OR IFNULL(menu_path, '') LIKE '%/exchange/exchange/%';

UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path LIKE '/bigdata-web/%';

UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path = '/bigdata-web';

UPDATE portal_nav_node
SET url = CONCAT('/bigdata-web', url)
WHERE status = 1
  AND url IS NOT NULL
  AND TRIM(url) <> ''
  AND url LIKE '/%'
  AND url NOT LIKE '/bigdata-web%'
  AND url NOT LIKE 'http://%'
  AND url NOT LIKE 'https://%';

UPDATE portal_nav_node
SET open_mode = 'route'
WHERE status = 1
  AND open_mode = 'new_tab'
  AND url LIKE '/bigdata-web/%';

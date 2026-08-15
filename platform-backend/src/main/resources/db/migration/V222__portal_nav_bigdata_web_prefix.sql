-- V222: 门户导航 url 补 /bigdata-web 前缀；menu_path 保持无此前缀供权限匹配
-- 根因：open_mode=new_tab 时 window.open('/exchange/...') 落到 nginx 404（SPA 在 /bigdata-web/）

-- 1) 修正历史笔误路径
UPDATE portal_nav_node
SET url = REPLACE(url, '/exchange/exchange/', '/exchange/'),
    menu_path = REPLACE(IFNULL(menu_path, ''), '/exchange/exchange/', '/exchange/')
WHERE IFNULL(url, '') LIKE '%/exchange/exchange/%'
   OR IFNULL(menu_path, '') LIKE '%/exchange/exchange/%';

-- 2) menu_path 去掉误加的前缀（与 sys_menu 对齐）
UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path LIKE '/bigdata-web/%';

UPDATE portal_nav_node
SET menu_path = SUBSTRING(menu_path, CHAR_LENGTH('/bigdata-web') + 1)
WHERE menu_path = '/bigdata-web';

-- 3) 站内 url 统一加 /bigdata-web（外链 http(s) 不动）
UPDATE portal_nav_node
SET url = CONCAT('/bigdata-web', url)
WHERE status = 1
  AND url IS NOT NULL
  AND TRIM(url) <> ''
  AND url LIKE '/%'
  AND url NOT LIKE '/bigdata-web%'
  AND url NOT LIKE 'http://%'
  AND url NOT LIKE 'https://%';

-- 4) 归集/应用等子系统改为站内 route（避免无必要新开标签 404）
UPDATE portal_nav_node
SET open_mode = 'route'
WHERE status = 1
  AND open_mode = 'new_tab'
  AND url LIKE '/bigdata-web/%';

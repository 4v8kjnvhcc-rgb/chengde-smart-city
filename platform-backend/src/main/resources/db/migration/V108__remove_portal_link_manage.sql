-- 移除「门户外链管理」菜单、权限与数据表

DELETE rm FROM sys_role_menu rm
INNER JOIN sys_menu m ON rm.menu_id = m.id
WHERE m.id IN (7713, 6001, 6002, 6003, 6004, 6005)
   OR m.permission LIKE 'system:portal-link:%'
   OR m.permission = 'hub:analytics:support:apps:links'
   OR IFNULL(m.path, '') LIKE '%tab=apps.links%'
   OR m.path = '/system/portal-links';

DELETE FROM sys_menu
WHERE id IN (7713, 6001, 6002, 6003, 6004, 6005)
   OR permission LIKE 'system:portal-link:%'
   OR permission = 'hub:analytics:support:apps:links'
   OR IFNULL(path, '') LIKE '%tab=apps.links%'
   OR path = '/system/portal-links';

DROP TABLE IF EXISTS portal_card_link;

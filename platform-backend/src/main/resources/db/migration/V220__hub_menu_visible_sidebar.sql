-- Hub 内页 visible=1：供各 Hub 侧栏「是否隐藏」生效；门户侧栏仍排除 hub 内页（?tab= / 内部分组）。
-- 「其他」分组及子项保持 visible=0（侧栏隐藏）。

UPDATE sys_menu
SET visible = 1
WHERE status = 1
  AND IFNULL(integration_type, '') = 'hub'
  AND (
    IFNULL(path, '') LIKE '%?tab=%'
    OR IFNULL(path, '') LIKE '%&tab=%'
    OR menu_type = 1
  )
  AND IFNULL(permission, '') NOT LIKE 'hub:analytics:support:other%'
  AND IFNULL(menu_name, '') <> '其他';

UPDATE sys_menu
SET visible = 0
WHERE status = 1
  AND (
    IFNULL(permission, '') LIKE 'hub:analytics:support:other%'
    OR (IFNULL(menu_name, '') = '其他' AND IFNULL(integration_type, '') = 'hub')
  );

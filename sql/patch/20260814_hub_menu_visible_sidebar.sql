-- 手工补丁（与 Flyway V220 同语义，可重复执行）
-- 目标库：smart_city
-- Hub 内页 visible=1 供侧栏「是否隐藏」；「其他」保持隐藏。

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

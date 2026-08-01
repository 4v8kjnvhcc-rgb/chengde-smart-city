-- V104: 恢复登记系统内置菜单可见，避免 Hub 侧栏只剩「菜单管理」
UPDATE sys_menu
SET visible = 1
WHERE id IN (7000, 7001, 7002, 7003, 7004, 7005, 7006, 7007, 7008, 7009, 7010, 7011, 7012, 7015)
  AND IFNULL(visible, 1) = 0;

-- 菜单管理固定排在登记子菜单末尾
UPDATE sys_menu
SET sort_order = 99
WHERE id = 7015 OR permission = 'hub:ingestion:register:menu-mgmt';

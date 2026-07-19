-- 菜单管理并入统一用户管理用户中心：侧栏「平台运维」不再单独展示菜单管理
UPDATE sys_menu SET status = 0 WHERE path = '/system/menus' AND id = 27;

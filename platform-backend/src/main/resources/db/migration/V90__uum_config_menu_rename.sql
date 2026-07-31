-- V90：UUM Hub「系统管理」侧栏/菜单更名为「系统维护管理」
UPDATE sys_menu SET menu_name = '系统维护管理'
WHERE id = 7604 AND path = '/system/uum?tab=config';

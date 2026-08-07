-- 部门数据共享门户菜单文案：共享资源→政务共享资源；资源订阅申请→基础资源目录
UPDATE sys_menu SET menu_name = '政务共享资源' WHERE id = 7102 AND menu_name = '共享资源';
UPDATE sys_menu SET menu_name = '基础资源目录' WHERE id = 7103 AND menu_name = '资源订阅申请';

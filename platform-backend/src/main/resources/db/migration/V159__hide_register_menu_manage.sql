-- V159: 数据资产登记系统不再提供「菜单管理」入口（统一用户管理仍可维护全部菜单）
UPDATE sys_menu
SET status = 0,
    visible = 0
WHERE id = 7015
   OR permission = 'hub:ingestion:register:menu-mgmt';

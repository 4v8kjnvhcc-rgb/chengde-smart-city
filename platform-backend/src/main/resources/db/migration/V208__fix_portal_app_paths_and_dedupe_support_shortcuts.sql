-- V208: 修复门户入口与角色菜单 path 不一致；去掉重复的任务管理/集成运维菜单
-- 1) 应用平台「基础库/重点领域统计」门户 menu_path 与 sys_menu 对齐，勾选后才能显示
-- 2) 通用支撑下 7882/7883 与统一用户内 7507/7508 重复，停用 7882/7883，避免「没勾门户项却因 Hub 项显示」

-- ========== A. 应用平台门户 path 对齐 ==========
UPDATE portal_nav_node
SET name = '基础库统计分析应用',
    url = '/exchange/application?app=stats-base&section=base',
    menu_path = '/exchange/application?app=stats-base&section=base'
WHERE id = 133;

UPDATE portal_nav_node
SET name = '重点领域统计分析应用',
    url = '/exchange/application?app=stats-domain&section=domain',
    menu_path = '/exchange/application?app=stats-domain&section=domain'
WHERE id = 134;

UPDATE portal_nav_node
SET url = '/exchange/application/assessment',
    menu_path = '/exchange/application/assessment'
WHERE id = 132
  AND (menu_path IS NULL OR menu_path = '');

UPDATE portal_nav_node
SET url = '/exchange/application/supply',
    menu_path = '/exchange/application/supply'
WHERE id = 131;

-- ========== B. 停用通用支撑下重复的任务管理/集成运维（保留统一用户内 7507/7508） ==========
UPDATE sys_menu
SET status = 0,
    visible = 0,
    menu_name = CONCAT(IFNULL(menu_name, ''), '(门户重复项已停用)')
WHERE id IN (7882, 7883)
  AND menu_name NOT LIKE '%已停用%';

-- 曾只勾 7882/7883 的角色，补授统一用户内对应项
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7507
FROM sys_role_menu rm
WHERE rm.menu_id = 7882
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 7507 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7507);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7508
FROM sys_role_menu rm
WHERE rm.menu_id = 7883
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 7508 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7508);

-- 清理已停用重复项的角色勾选
DELETE rm FROM sys_role_menu rm
WHERE rm.menu_id IN (7882, 7883);

-- ========== C. 通用支撑下仅保留：统一用户 + 智能BI ==========
UPDATE sys_menu
SET parent_id = 7880, sort_order = 1, status = 1, visible = 0
WHERE id = 13;
UPDATE sys_menu
SET parent_id = 7880, sort_order = 2, status = 1, visible = 0
WHERE id = 14;

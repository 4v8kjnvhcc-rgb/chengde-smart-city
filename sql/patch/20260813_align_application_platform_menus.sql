-- V203: 应用平台角色配置树与门户下拉对齐（仅四应用入口）
-- 根因：6102/6103 挂在 parent=6 且 status=1、visible=0 → 门户隐藏，角色配置仍显示

-- ========== A. 停用应用平台下重复的「供需配置」(6102) ==========
-- 真实入口已在供需 Hub：7857（系统管理 → 供需配置）
UPDATE sys_menu
SET status = 0,
    visible = 0,
    menu_name = '供需配置(已并入供需对接系统)'
WHERE id = 6102;

-- 旧授 6102 → 补授 Hub 供需配置叶子（及父级）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, t.menu_id
FROM sys_role_menu rm
CROSS JOIN (
  SELECT 7130 AS menu_id UNION ALL
  SELECT 7859 UNION ALL
  SELECT 7857
) t
WHERE rm.menu_id = 6102
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.menu_id AND m.status = 1)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = t.menu_id
  );

-- ========== B. 「考核评估配置」挂到考核评估系统下（与供需配置挂供需 Hub 同构） ==========
UPDATE sys_menu
SET parent_id = 7131,
    sort_order = 1,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    path = '/exchange/application/assessment-config',
    component = 'system/AssessmentConfigView',
    permission = 'hub:application:assessment:config',
    menu_name = '考核评估配置'
WHERE id = 6103;

-- 确保四应用入口名称/路径/顺序与门户一致
UPDATE sys_menu
SET parent_id = 6,
    status = 1,
    visible = 1,
    sort_order = 1,
    menu_name = '数据供需对接系统',
    path = '/exchange/application/supply',
    permission = 'hub:application:supply'
WHERE id = 7130;

UPDATE sys_menu
SET parent_id = 6,
    status = 1,
    visible = 1,
    sort_order = 2,
    menu_name = '考核评估系统',
    path = '/exchange/application/assessment',
    permission = 'hub:application:assessment'
WHERE id = 7131;

UPDATE sys_menu
SET parent_id = 6,
    status = 1,
    visible = 1,
    sort_order = 3,
    menu_name = '基础库统计分析应用',
    path = '/exchange/application?app=stats-base&section=base',
    permission = 'analytics:stats:view'
WHERE id = 7132;

UPDATE sys_menu
SET parent_id = 6,
    status = 1,
    visible = 1,
    sort_order = 4,
    menu_name = '重点领域统计分析应用',
    path = '/exchange/application?app=stats-domain&section=domain',
    permission = 'analytics:stats:view'
WHERE id = 7133;

-- 应用平台根
UPDATE sys_menu
SET menu_name = '应用平台',
    status = 1,
    sort_order = 6
WHERE id = 6;

-- 清理：应用平台下除四应用外、仍挂 parent=6 的可见杂项（旧门户/统计壳等）
UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 6
  AND id NOT IN (7130, 7131, 7132, 7133)
  AND status = 1
  AND id NOT IN (6103);

-- 曾授 6103 的角色：确保能看到父级考核系统
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7131
FROM sys_role_menu rm
WHERE rm.menu_id = 6103
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = 7131 AND m.status = 1)
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7131
  );

-- SYSTEM_ADMIN 授考核配置叶子
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, 6103
FROM DUAL
WHERE EXISTS (SELECT 1 FROM sys_menu WHERE id = 6103 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 6103);

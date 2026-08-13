-- V209: 通用支撑平台下恢复「任务管理 / 集成运维」配置入口
-- 门户与角色/菜单配置以 7882/7883（挂 7880）为准；停用 UUM 内重复 7507/7508，避免双处勾选不一致

-- ========== A. 恢复通用支撑下门户快捷项 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7882, 7880, '任务管理', 2, '/analytics/support?tab=tasks', NULL,
       'hub:analytics:support:tasks', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7882);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7883, 7880, '集成运维', 2, '/analytics/support?tab=ops.kettle', NULL,
       'hub:analytics:support:ops', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7883);

UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 3,
    status = 1,
    visible = 0,
    menu_name = '任务管理',
    path = '/analytics/support?tab=tasks',
    permission = 'hub:analytics:support:tasks',
    menu_type = 2,
    integration_type = 'hub'
WHERE id = 7882;

UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 4,
    status = 1,
    visible = 0,
    menu_name = '集成运维',
    path = '/analytics/support?tab=ops.kettle',
    permission = 'hub:analytics:support:ops',
    menu_type = 2,
    integration_type = 'hub'
WHERE id = 7883;

-- ========== B. 曾只勾 UUM 内 7507/7508 的角色，迁到通用支撑入口 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7882
FROM sys_role_menu rm
WHERE rm.menu_id = 7507
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 7882 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7882);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, 7883
FROM sys_role_menu rm
WHERE rm.menu_id = 7508
  AND EXISTS (SELECT 1 FROM sys_menu WHERE id = 7883 AND status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = 7883);

-- ========== C. 停用 UUM 内重复项（Hub 侧栏仍按 permission 显示） ==========
UPDATE sys_menu
SET status = 0,
    visible = 0,
    menu_name = CASE
      WHEN menu_name LIKE '%配置入口已迁至通用支撑%' THEN menu_name
      ELSE CONCAT(IFNULL(menu_name, ''), '(配置入口已迁至通用支撑)')
    END
WHERE id IN (7507, 7508);

DELETE rm FROM sys_role_menu rm WHERE rm.menu_id IN (7507, 7508);

-- ========== D. 通用支撑子树仅保留：统一用户 / 智能BI / 任务管理 / 集成运维 ==========
UPDATE sys_menu SET parent_id = 7880, sort_order = 1, status = 1, visible = 0 WHERE id = 13;
UPDATE sys_menu SET parent_id = 7880, sort_order = 2, status = 1, visible = 0 WHERE id = 14;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (7882, 7883)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

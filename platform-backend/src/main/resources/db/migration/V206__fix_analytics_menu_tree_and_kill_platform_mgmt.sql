-- V206: 固化「大数据挖掘分析平台」配置树与门户一致，并永久停用旧平台管理
-- 根因：HubSidebarMenuBootstrap 每次启动把 id=19/640x/6500 status=1 复活

-- ========== A. 停用旧平台管理 / 集成运维一级壳 ==========
UPDATE sys_menu
SET status = 0, visible = 0
WHERE status = 1
  AND (
    id IN (19, 27, 6400, 6401, 6402, 6403, 6500, 30, 31)
    OR path = '/system'
    OR path = '/integration'
    OR path LIKE '/system/%'
  );

-- ========== B. 挖掘分析：门户同构两级 ==========
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7880, 12, '通用支撑平台', 1, '/analytics/general-support', NULL,
       'hub:analytics:general-support', NULL, 1, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7880);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7881, 12, '业务支撑平台', 1, '/analytics/business-support', NULL,
       'hub:analytics:business-support', NULL, 2, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7881);

UPDATE sys_menu
SET parent_id = 12, status = 1, visible = 0, sort_order = 1,
    menu_name = '通用支撑平台', path = '/analytics/general-support',
    menu_type = 1, integration_type = 'hub',
    permission = 'hub:analytics:general-support'
WHERE id = 7880;

UPDATE sys_menu
SET parent_id = 12, status = 1, visible = 0, sort_order = 2,
    menu_name = '业务支撑平台', path = '/analytics/business-support',
    menu_type = 1, integration_type = 'hub',
    permission = 'hub:analytics:business-support'
WHERE id = 7881;

UPDATE sys_menu
SET parent_id = 7880, sort_order = 1, status = 1, visible = 0,
    menu_name = '统一用户管理系统', path = '/analytics/support', menu_type = 2, integration_type = 'hub'
WHERE id = 13;

UPDATE sys_menu
SET parent_id = 7880, sort_order = 2, status = 1, visible = 0,
    menu_name = '智能BI平台', path = '/analytics/bi', menu_type = 2, integration_type = 'hub'
WHERE id = 14;

-- 门户飞出同级：任务管理 / 集成运维（与 UUM 内 7507/7508 同 permission）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7882, 7880, '任务管理', 2, '/analytics/support?tab=tasks', NULL,
       'hub:analytics:support:tasks', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7882);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7883, 7880, '集成运维', 2, '/analytics/support?tab=ops.kettle', NULL,
       'hub:analytics:support:ops', NULL, 4, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7883);

UPDATE sys_menu
SET parent_id = 7880, sort_order = 3, status = 1, visible = 0,
    menu_name = '任务管理', path = '/analytics/support?tab=tasks',
    permission = 'hub:analytics:support:tasks', menu_type = 2, integration_type = 'hub'
WHERE id = 7882;

UPDATE sys_menu
SET parent_id = 7880, sort_order = 4, status = 1, visible = 0,
    menu_name = '集成运维', path = '/analytics/support?tab=ops.kettle',
    permission = 'hub:analytics:support:ops', menu_type = 2, integration_type = 'hub'
WHERE id = 7883;

UPDATE sys_menu SET parent_id = 7881, sort_order = 1, status = 1, visible = 0, menu_name = '人口大数据支撑系统', path = '/analytics/population' WHERE id = 15;
UPDATE sys_menu SET parent_id = 7881, sort_order = 2, status = 1, visible = 0, menu_name = '法人大数据支撑系统', path = '/analytics/legal-entity' WHERE id = 16;
UPDATE sys_menu SET parent_id = 7881, sort_order = 3, status = 1, visible = 0, menu_name = '宏观经济及工业运行大数据支撑系统', path = '/analytics/macro' WHERE id = 17;
UPDATE sys_menu SET parent_id = 7881, sort_order = 4, status = 1, visible = 0, menu_name = '重点领域示范应用支撑系统', path = '/analytics/key-domains' WHERE id = 18;

UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 12
  AND id NOT IN (7880, 7881)
  AND status = 1;

UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 7880
  AND id NOT IN (13, 14, 7882, 7883)
  AND status = 1;

-- ========== C. 统一用户 Hub 侧栏（含任务/运维） ==========
UPDATE sys_menu SET parent_id = 13, sort_order = 1, status = 1, visible = 0, menu_name = '用户中心' WHERE id = 7500;
UPDATE sys_menu SET parent_id = 13, sort_order = 2, status = 1, visible = 0, menu_name = '应用中心' WHERE id = 7501;
UPDATE sys_menu SET parent_id = 13, sort_order = 3, status = 1, visible = 0, menu_name = '认证中心' WHERE id = 7502;
UPDATE sys_menu SET parent_id = 13, sort_order = 4, status = 1, visible = 0, menu_name = '服务中心' WHERE id = 7503;
UPDATE sys_menu SET parent_id = 13, sort_order = 5, status = 1, visible = 0, menu_name = '任务管理', path = '/analytics/support?tab=tasks', permission = 'hub:analytics:support:tasks' WHERE id = 7507;
UPDATE sys_menu SET parent_id = 13, sort_order = 6, status = 1, visible = 0, menu_name = '集成运维', path = '/analytics/support?tab=ops.kettle', permission = 'hub:analytics:support:ops' WHERE id = 7508;
UPDATE sys_menu SET parent_id = 13, sort_order = 7, status = 1, visible = 0, menu_name = '系统管理' WHERE id = 7504;
UPDATE sys_menu SET parent_id = 13, sort_order = 8, status = 1, visible = 0, menu_name = '日志审计' WHERE id = 7505;
UPDATE sys_menu SET parent_id = 13, sort_order = 9, status = 1, visible = 0, menu_name = '其他' WHERE id = 7506;

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission, icon, sort_order, m_code, integration_type, status, visible)
SELECT 7722, 7504, '系统配置', 1, NULL, NULL, 'hub:analytics:support:sys:cfg', NULL, 3, NULL, 'hub', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 7722);

UPDATE sys_menu SET parent_id = 7504, sort_order = 3, status = 1, visible = 0, menu_name = '系统配置', menu_type = 1 WHERE id = 7722;
UPDATE sys_menu SET parent_id = 7722, sort_order = 1, status = 1, visible = 0, menu_name = '通用配置' WHERE id = 7727;
UPDATE sys_menu SET parent_id = 7722, sort_order = 2, status = 1, visible = 0, menu_name = '基础信息' WHERE id = 7723;
UPDATE sys_menu SET parent_id = 7722, sort_order = 3, status = 1, visible = 0, menu_name = '系统邮箱' WHERE id = 7724;
UPDATE sys_menu SET parent_id = 7722, sort_order = 4, status = 1, visible = 0, menu_name = '执行周期管理', path = '/analytics/support?tab=sys.cfg.cron' WHERE id = 7800;

-- ========== D. 授权 ==========
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (12, 7880, 7881, 7882, 7883, 13, 14, 15, 16, 17, 18, 7507, 7508, 7722)
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, t.menu_id
FROM sys_role_menu rm
CROSS JOIN (
  SELECT 7880 AS menu_id UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 7882 UNION ALL SELECT 7883
) t
WHERE rm.menu_id IN (19, 6400, 6401, 6402, 6500, 27)
  AND EXISTS (SELECT 1 FROM sys_menu m WHERE m.id = t.menu_id AND m.status = 1)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = rm.role_id AND x.menu_id = t.menu_id);

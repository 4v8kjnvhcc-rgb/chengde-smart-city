-- V205: 「统一用户管理系统」配置树对齐 Hub 侧栏（AnalyticsSupportHubView NAV_BASE）
-- V204 将 7507/7508 挂到通用支撑下与门户飞出并列，但进入统一用户后侧栏仍含任务管理/集成运维

-- ========== A. 任务管理 / 集成运维挂回统一用户（id=13） ==========
UPDATE sys_menu
SET parent_id = 13,
    sort_order = 5,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '任务管理',
    path = '/analytics/support?tab=tasks',
    permission = 'hub:analytics:support:tasks'
WHERE id = 7507;

UPDATE sys_menu
SET parent_id = 13,
    sort_order = 6,
    status = 1,
    visible = 0,
    integration_type = 'hub',
    menu_name = '集成运维',
    path = '/analytics/support?tab=ops.kettle',
    permission = 'hub:analytics:support:ops'
WHERE id = 7508;

-- ========== B. 统一用户下一级顺序与 Hub 一致 ==========
UPDATE sys_menu SET parent_id = 13, sort_order = 1, menu_name = '用户中心', status = 1, visible = 0 WHERE id = 7500;
UPDATE sys_menu SET parent_id = 13, sort_order = 2, menu_name = '应用中心', status = 1, visible = 0 WHERE id = 7501;
UPDATE sys_menu SET parent_id = 13, sort_order = 3, menu_name = '认证中心', status = 1, visible = 0 WHERE id = 7502;
UPDATE sys_menu SET parent_id = 13, sort_order = 4, menu_name = '服务中心', status = 1, visible = 0 WHERE id = 7503;
UPDATE sys_menu SET parent_id = 13, sort_order = 7, menu_name = '系统管理', status = 1, visible = 0 WHERE id = 7504;
UPDATE sys_menu SET parent_id = 13, sort_order = 8, menu_name = '日志审计', status = 1, visible = 0 WHERE id = 7505;
UPDATE sys_menu SET parent_id = 13, sort_order = 9, menu_name = '其他', status = 1, visible = 0 WHERE id = 7506;

UPDATE sys_menu
SET menu_name = '统一用户管理系统',
    path = '/analytics/support',
    status = 1,
    visible = 0,
    parent_id = 7880,
    sort_order = 1
WHERE id = 13;

-- ========== C. 叶子对齐 Hub（名称/父级/顺序） ==========
-- 用户中心
UPDATE sys_menu SET parent_id = 7500, sort_order = 1, menu_name = '组织管理', status = 1, visible = 0 WHERE id = 7700;
UPDATE sys_menu SET parent_id = 7500, sort_order = 2, menu_name = '用户管理', status = 1, visible = 0 WHERE id = 7701;
UPDATE sys_menu SET parent_id = 7500, sort_order = 3, menu_name = '角色管理', status = 1, visible = 0 WHERE id = 7702;
UPDATE sys_menu SET parent_id = 7500, sort_order = 4, menu_name = '集群管理', status = 1, visible = 0 WHERE id = 7703;

-- 应用中心（无门户外链）
UPDATE sys_menu SET parent_id = 7501, sort_order = 1, menu_name = '应用管理', status = 1, visible = 0 WHERE id = 7710;
UPDATE sys_menu SET parent_id = 7501, sort_order = 2, menu_name = '系统对接', status = 1, visible = 0 WHERE id = 7711;
UPDATE sys_menu SET parent_id = 7501, sort_order = 3, menu_name = '门户配置', status = 1, visible = 0 WHERE id = 7712;
UPDATE sys_menu SET status = 0, visible = 0 WHERE id = 7713 OR permission = 'hub:analytics:support:apps:links';

-- 系统管理
UPDATE sys_menu SET parent_id = 7504, sort_order = 1, menu_name = '菜单管理', status = 1, visible = 0 WHERE id = 7720;
UPDATE sys_menu SET parent_id = 7504, sort_order = 2, menu_name = '字典管理', status = 1, visible = 0 WHERE id = 7721;
UPDATE sys_menu SET parent_id = 7504, sort_order = 3, menu_name = '系统配置', status = 1, visible = 0 WHERE id = 7722;
UPDATE sys_menu SET parent_id = 7504, sort_order = 4, menu_name = '标签库', status = 1, visible = 0 WHERE id = 7725;
UPDATE sys_menu SET parent_id = 7504, sort_order = 5, menu_name = '内置属性管理', status = 1, visible = 0,
    path = '/analytics/support?tab=sys.builtin', permission = 'hub:analytics:support:sys:builtin'
WHERE id = 7726;

-- 系统配置子项：通用配置 / 基础信息 / 系统邮箱 / 执行周期
UPDATE sys_menu SET parent_id = 7722, sort_order = 1, menu_name = '通用配置', status = 1, visible = 0 WHERE id = 7727;
UPDATE sys_menu SET parent_id = 7722, sort_order = 2, menu_name = '基础信息', status = 1, visible = 0 WHERE id = 7723;
UPDATE sys_menu SET parent_id = 7722, sort_order = 3, menu_name = '系统邮箱', status = 1, visible = 0 WHERE id = 7724;
UPDATE sys_menu SET parent_id = 7722, sort_order = 4, menu_name = '执行周期管理', status = 1, visible = 0,
    path = '/analytics/support?tab=sys.cfg.cron', permission = 'hub:analytics:support:sys:cron'
WHERE id = 7800;

-- 日志审计 / 其他
UPDATE sys_menu SET parent_id = 7505, sort_order = 1, menu_name = '日志审计', status = 1, visible = 0 WHERE id = 7730;
UPDATE sys_menu SET parent_id = 7505, sort_order = 2, menu_name = '访问控制', status = 1, visible = 0 WHERE id = 7731;
UPDATE sys_menu SET parent_id = 7505, sort_order = 3, menu_name = '等保安全', status = 1, visible = 0 WHERE id = 7732;
UPDATE sys_menu SET parent_id = 7506, sort_order = 1, menu_name = '角色菜单权限', status = 1, visible = 0 WHERE id = 7740;
UPDATE sys_menu SET parent_id = 7506, sort_order = 2, menu_name = '对接探测', status = 1, visible = 0 WHERE id = 7741;

-- ========== D. 通用支撑下仅保留：统一用户 + 智能BI（任务/运维在统一用户内，与 Hub 一致） ==========
UPDATE sys_menu
SET parent_id = 7880,
    sort_order = 2,
    status = 1,
    visible = 0,
    menu_name = '智能BI平台',
    path = '/analytics/bi'
WHERE id = 14;

-- 清理：统一用户下仅保留 Hub 九项
UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 13
  AND id NOT IN (7500, 7501, 7502, 7503, 7504, 7505, 7506, 7507, 7508)
  AND status = 1;

-- 清理：通用支撑下仅 UUM + BI
UPDATE sys_menu
SET status = 0, visible = 0
WHERE parent_id = 7880
  AND id NOT IN (13, 14)
  AND status = 1;

-- ========== E. 门户飞出：任务/运维仍指向统一用户 Hub Tab（portal_nav 保持四入口） ==========
UPDATE portal_nav_node
SET url = '/analytics/support?tab=tasks',
    menu_path = '/analytics/support?tab=tasks',
    name = '任务管理'
WHERE id = 313;
UPDATE portal_nav_node
SET url = '/analytics/support?tab=ops.kettle',
    menu_path = '/analytics/support?tab=ops.kettle',
    name = '集成运维'
WHERE id = 314;
UPDATE portal_nav_node SET name = '统一用户管理系统', url = '/analytics/support', menu_path = '/analytics/support' WHERE id = 311;

-- 授权：SYSTEM_ADMIN 补齐
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.id IN (
    13, 14, 7500, 7501, 7502, 7503, 7504, 7505, 7506, 7507, 7508,
    7700, 7701, 7702, 7703, 7710, 7711, 7712,
    7720, 7721, 7722, 7723, 7724, 7725, 7726, 7727, 7800,
    7730, 7731, 7732, 7740, 7741
  )
  AND m.status = 1
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

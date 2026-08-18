-- 手工补丁（对应 Flyway V235）：任务管理/集成运维挂到统一用户(id=13)
-- 目标库：smart_city
-- 可重复执行；不修改 visible、不改 permission、不删 sys_role_menu

UPDATE sys_menu
SET parent_id = 13,
    sort_order = 5,
    status = 1,
    menu_name = '任务管理',
    path = '/analytics/support?tab=tasks',
    permission = 'hub:analytics:support:tasks',
    menu_type = 2,
    integration_type = 'hub'
WHERE id = 7882;

UPDATE sys_menu
SET parent_id = 13,
    sort_order = 6,
    status = 1,
    menu_name = '集成运维',
    path = '/analytics/support?tab=ops.kettle',
    permission = 'hub:analytics:support:ops',
    menu_type = 2,
    integration_type = 'hub'
WHERE id = 7883;

UPDATE sys_menu
SET status = 0
WHERE id IN (7507, 7508)
  AND status <> 0;

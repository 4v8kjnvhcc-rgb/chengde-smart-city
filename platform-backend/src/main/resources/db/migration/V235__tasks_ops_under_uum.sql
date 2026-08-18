-- V235: 「任务管理 / 集成运维」挂回统一用户管理系统(id=13)
-- 不改 permission / path / role_menu / visible，避免影响授权与「是否隐藏」
-- 与 HubSidebarMenuBootstrap 对齐，防止启动时被挂回通用支撑(7880)

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

-- 旧重复入口（曾挂 UUM 的 7507/7508）保持停用，防止角色树双份勾选
UPDATE sys_menu
SET status = 0
WHERE id IN (7507, 7508)
  AND status <> 0;

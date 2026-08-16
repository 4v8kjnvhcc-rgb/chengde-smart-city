-- 手工补丁：下线通用支撑「其他」侧栏（角色菜单权限 / 对接探测）
-- 目标库：smart_city
-- 可重复执行；与 Flyway V231__disable_support_other_menus.sql 同内容
-- 若环境已跑过 V231，无需再手工执行
-- 保留入口：用户中心·角色管理；应用中心·系统对接

UPDATE sys_menu
SET status = 0, visible = 0
WHERE id IN (7506, 7740, 7741)
   OR IFNULL(permission, '') IN (
        'hub:analytics:support:other',
        'hub:analytics:support:other:roleMenus',
        'hub:analytics:support:other:probe'
      )
   OR IFNULL(path, '') IN (
        '/analytics/support?tab=other.roleMenus',
        '/analytics/support?tab=other.probe'
      );

-- 下线通用支撑「其他」侧栏（角色菜单权限 / 对接探测）：功能已分别在「用户中心·角色管理」「应用中心·系统对接」
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

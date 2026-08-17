package com.chengde.smartcity.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengde.smartcity.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT r.role_code FROM sys_role r JOIN sys_user_role ur ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(Long userId);

    /**
     * 用户权限码：启用菜单上的 permission +
     * 已停用「按钮」菜单（menu_type=3）在父级已授权时自动继承（不再单独配置按钮权限）。
     */
    @Select("""
            SELECT DISTINCT x.permission FROM (
              SELECT m.permission AS permission
              FROM sys_menu m
              JOIN sys_role_menu rm ON m.id = rm.menu_id
              JOIN sys_user_role ur ON rm.role_id = ur.role_id
              WHERE ur.user_id = #{userId}
                AND m.status = 1
                AND m.menu_type <> 3
                AND m.permission IS NOT NULL AND m.permission <> ''
              UNION
              SELECT b.permission AS permission
              FROM sys_menu b
              JOIN sys_role_menu rm ON rm.menu_id = b.parent_id
              JOIN sys_user_role ur ON rm.role_id = ur.role_id
              WHERE ur.user_id = #{userId}
                AND b.menu_type = 3
                AND b.permission IS NOT NULL AND b.permission <> ''
              UNION
              SELECT map.old_perm AS permission
              FROM sys_role_menu rm
              JOIN sys_user_role ur ON rm.role_id = ur.role_id
              JOIN sys_menu m ON m.id = rm.menu_id AND m.status = 1
              JOIN (
                SELECT 'hub:system:uum:users' AS old_perm, 'hub:analytics:support:users:user' AS new_perm UNION ALL
                SELECT 'hub:system:uum:apps', 'hub:analytics:support:apps:manage' UNION ALL
                SELECT 'hub:system:uum:auth', 'hub:analytics:support:auth' UNION ALL
                SELECT 'hub:system:uum:services', 'hub:analytics:support:services' UNION ALL
                SELECT 'hub:system:uum:audit', 'hub:analytics:support:audit:log' UNION ALL
                SELECT 'hub:system:uum:integration', 'hub:analytics:support:apps:integration' UNION ALL
                SELECT 'hub:system:uum:portal', 'hub:analytics:support:apps:portal' UNION ALL
                SELECT 'system:user:list', 'hub:analytics:support:users:user' UNION ALL
                SELECT 'system:role:list', 'hub:analytics:support:users:role' UNION ALL
                SELECT 'system:org:list', 'hub:analytics:support:users:org' UNION ALL
                SELECT 'system:menu:list', 'hub:analytics:support:sys:menus' UNION ALL
                SELECT 'system:audit:list', 'hub:analytics:support:audit:log' UNION ALL
                SELECT 'system:security:config', 'hub:analytics:support:audit:security' UNION ALL
                SELECT 'portal:supply:create', 'hub:application:supply:demand' UNION ALL
                SELECT 'portal:supply:create', 'hub:application:supply:confirm' UNION ALL
                SELECT 'portal:supply:approve', 'hub:application:supply:analysis' UNION ALL
                SELECT 'portal:supply:approve', 'hub:application:supply:supervise' UNION ALL
                SELECT 'portal:supply:approve', 'hub:application:supply:home' UNION ALL
                SELECT 'system:exchange:supply-config', 'hub:application:supply:config' UNION ALL
                SELECT 'system:exchange:assessment-config', 'hub:application:assessment:config'
              ) map ON map.new_perm = m.permission
              WHERE ur.user_id = #{userId}
            ) x
            """)
    List<String> findPermissionsByUserId(Long userId);
}

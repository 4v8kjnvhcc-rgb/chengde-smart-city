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

    @Select("SELECT DISTINCT m.permission FROM sys_menu m JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "JOIN sys_user_role ur ON rm.role_id = ur.role_id WHERE ur.user_id = #{userId} AND m.permission IS NOT NULL")
    List<String> findPermissionsByUserId(Long userId);
}

package com.chengde.smartcity.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chengde.smartcity.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    @Select("SELECT DISTINCT m.* FROM sys_menu m JOIN sys_role_menu rm ON m.id = rm.menu_id "
            + "JOIN sys_user_role ur ON rm.role_id = ur.role_id WHERE ur.user_id = #{userId} AND m.status = 1 ORDER BY m.sort_order")
    List<SysMenu> findMenusByUserId(Long userId);
}

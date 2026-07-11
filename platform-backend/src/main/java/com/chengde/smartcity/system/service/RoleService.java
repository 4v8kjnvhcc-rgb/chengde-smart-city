package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.RoleMenuAssignRequest;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.mapper.SysRoleMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;

    public RoleService(SysRoleMapper roleMapper, JdbcTemplate jdbcTemplate, AuditService auditService) {
        this.roleMapper = roleMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public List<SysRole> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1));
    }

    @Transactional
    public void assignMenus(UserPrincipal operator, Long roleId, RoleMenuAssignRequest request) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        if ("SYSTEM_ADMIN".equals(role.getRoleCode()) && !operator.isSystemAdmin()) {
            throw new BusinessException(403, "无权修改系统管理员角色");
        }
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        for (Long menuId : request.menuIds()) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ROLE_MENU_ASSIGN", "sys_role", String.valueOf(roleId), role.getRoleCode());
    }

    public List<Long> menuIdsOfRole(Long roleId) {
        return jdbcTemplate.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId);
    }
}

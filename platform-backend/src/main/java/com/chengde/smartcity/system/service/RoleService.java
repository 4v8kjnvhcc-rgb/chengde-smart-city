package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.SecurityUserDetailsService;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.RoleCreateRequest;
import com.chengde.smartcity.system.dto.RoleMenuAssignRequest;
import com.chengde.smartcity.system.dto.RoleUpdateRequest;
import com.chengde.smartcity.system.entity.SysRole;
import com.chengde.smartcity.system.mapper.SysRoleMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final SysRoleMapper roleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final SecurityUserDetailsService userDetailsService;

    public RoleService(SysRoleMapper roleMapper, JdbcTemplate jdbcTemplate, AuditService auditService,
                       SecurityUserDetailsService userDetailsService) {
        this.roleMapper = roleMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.userDetailsService = userDetailsService;
    }

    public List<SysRole> list(String keyword, Boolean includeDisabled) {
        LambdaQueryWrapper<SysRole> q = new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId);
        if (includeDisabled == null || !includeDisabled) {
            q.eq(SysRole::getStatus, 1);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysRole::getRoleCode, kw).or().like(SysRole::getRoleName, kw));
        }
        return roleMapper.selectList(q);
    }

    @Transactional
    public Long create(UserPrincipal operator, RoleCreateRequest req) {
        String code = req.roleCode().trim().toUpperCase();
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, code)) > 0) {
            throw new BusinessException(400, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(req.roleName().trim());
        role.setDescription(req.description() == null ? null : req.description().trim());
        role.setRoleType(req.roleType() == null ? 2 : req.roleType());
        role.setStatus(1);
        roleMapper.insert(role);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ROLE_CREATE", "sys_role", String.valueOf(role.getId()), role.getRoleCode());
        log.info("role created id={} code={} by={}", role.getId(), role.getRoleCode(), operator.getUsername());
        return role.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, RoleUpdateRequest req) {
        SysRole role = requireRole(id);
        if ("SYSTEM_ADMIN".equals(role.getRoleCode()) && !operator.isSystemAdmin()) {
            throw new BusinessException(403, "无权修改系统管理员角色");
        }
        if (req.roleName() != null && !req.roleName().isBlank()) {
            role.setRoleName(req.roleName().trim());
        }
        if (req.description() != null) {
            role.setDescription(req.description().trim());
        }
        if (req.status() != null) {
            if ("SYSTEM_ADMIN".equals(role.getRoleCode()) && req.status() != 1) {
                throw new BusinessException(400, "系统管理员角色不可停用");
            }
            role.setStatus(req.status());
        }
        roleMapper.updateById(role);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ROLE_UPDATE", "sys_role", String.valueOf(id), role.getRoleCode());
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        SysRole role = requireRole(id);
        if ("SYSTEM_ADMIN".equals(role.getRoleCode()) || Integer.valueOf(1).equals(role.getRoleType())) {
            throw new BusinessException(400, "系统内置角色不可删除");
        }
        if (!operator.isSystemAdmin() && "SYSTEM_ADMIN".equals(role.getRoleCode())) {
            throw new BusinessException(403, "无权删除系统管理员角色");
        }
        Integer bound = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE role_id = ?", Integer.class, id);
        if (bound != null && bound > 0) {
            throw new BusinessException(400, "该角色仍有用户绑定，无法删除");
        }
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", id);
        roleMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ROLE_DELETE", "sys_role", String.valueOf(id), role.getRoleCode());
        log.info("role deleted id={} code={} by={}", id, role.getRoleCode(), operator.getUsername());
    }

    @Transactional
    public void assignMenus(UserPrincipal operator, Long roleId, RoleMenuAssignRequest request) {
        SysRole role = requireRole(roleId);
        if ("SYSTEM_ADMIN".equals(role.getRoleCode()) && !operator.isSystemAdmin()) {
            throw new BusinessException(403, "无权修改系统管理员角色");
        }
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        if ("SYSTEM_ADMIN".equals(role.getRoleCode())) {
            // 系统管理员角色始终全量授权（含侧栏隐藏但仍需权限码的菜单）；忽略勾选削减
            jdbcTemplate.update(
                    "INSERT INTO sys_role_menu (role_id, menu_id) "
                            + "SELECT ?, id FROM sys_menu m WHERE "
                            + "(m.status = 1 OR (m.permission IS NOT NULL AND m.permission <> '')) "
                            + "AND IFNULL(m.integration_type,'') <> 'catalog' "
                            + "AND IFNULL(m.menu_name,'') NOT LIKE '%D05%' "
                            + "AND IFNULL(m.menu_name,'') NOT LIKE '%已并入%'",
                    roleId);
            log.info("SYSTEM_ADMIN menus forced to full set by {}", operator.getUsername());
        } else {
            // 只持久化叶子/空目录：全选时前端可能把有下级的父 id 一并提交；
            // 父节点若 visible=0（Hub 壳），用户菜单树含该父后侧栏会按血缘整枝裁掉。
            Set<Long> parentsWithChildren = new HashSet<>(jdbcTemplate.queryForList(
                    "SELECT DISTINCT parent_id FROM sys_menu WHERE parent_id IS NOT NULL AND parent_id <> 0 "
                            + "AND IFNULL(status,1) <> 0",
                    Long.class));
            for (Long menuId : request.menuIds()) {
                if (menuId == null || parentsWithChildren.contains(menuId)) {
                    continue;
                }
                jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "ROLE_MENU_ASSIGN", "sys_role", String.valueOf(roleId), role.getRoleCode());
        // 菜单变更后立即失效该角色下用户的权限缓存，避免侧栏仍按旧权限全量展示
        List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM sys_user_role WHERE role_id = ?", Long.class, roleId);
        for (Long uid : userIds) {
            userDetailsService.evictPrincipalCache(uid);
        }
    }

    public List<Long> menuIdsOfRole(Long roleId) {
        return jdbcTemplate.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId);
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在");
        }
        return role;
    }
}

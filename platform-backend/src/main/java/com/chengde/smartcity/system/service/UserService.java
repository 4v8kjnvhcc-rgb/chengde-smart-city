package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.UserCreateRequest;
import com.chengde.smartcity.system.dto.UserUpdateRequest;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public UserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                       AuditService auditService, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<SysUser> page(UserPrincipal operator, int page, int size, String keyword) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        if (!operator.isSystemAdmin()) {
            q.eq(SysUser::getOrgId, operator.getOrgId());
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        q.orderByDesc(SysUser::getId);
        return userMapper.selectPage(new Page<>(page, size), q);
    }

    @Transactional
    public Long create(UserPrincipal operator, UserCreateRequest req) {
        assertOrgAccess(operator, req.orgId());
        validatePassword(req.password());
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username())) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDisplayName(req.displayName());
        user.setOrgId(req.orgId());
        user.setStatus(1);
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.insert(user);
        if (req.roleIds() != null) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", user.getId());
            for (Long roleId : req.roleIds()) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", user.getId(), roleId);
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "USER_CREATE", "sys_user", String.valueOf(user.getId()), req.username());
        return user.getId();
    }

    @Transactional
    public void update(UserPrincipal operator, Long id, UserUpdateRequest req) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        assertOrgAccess(operator, user.getOrgId());
        if (req.displayName() != null) {
            user.setDisplayName(req.displayName());
        }
        if (req.status() != null) {
            user.setStatus(req.status());
        }
        if (req.orgId() != null && operator.isSystemAdmin()) {
            user.setOrgId(req.orgId());
        }
        userMapper.updateById(user);
        if (req.roleIds() != null) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
            for (Long roleId : req.roleIds()) {
                jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
            }
        }
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "USER_UPDATE", "sys_user", String.valueOf(id), user.getUsername());
    }

    public void assertOrgAccess(UserPrincipal operator, Long targetOrgId) {
        if (operator.isSystemAdmin()) {
            return;
        }
        if (!operator.getOrgId().equals(targetOrgId)) {
            throw new BusinessException(403, "无权操作其他机构用户");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException(400, "密码至少 8 位");
        }
        boolean hasLetter = password.matches(".*[A-Za-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(400, "密码须包含字母和数字");
        }
    }

    public List<Long> roleIdsOfUser(Long userId) {
        return jdbcTemplate.queryForList("SELECT role_id FROM sys_user_role WHERE user_id = ?", Long.class, userId);
    }
}

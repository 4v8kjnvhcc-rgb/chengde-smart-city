package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.auth.TransportCryptoService;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.security.SecurityUserDetailsService;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.dto.UserCreateRequest;
import com.chengde.smartcity.system.dto.UserListItem;
import com.chengde.smartcity.system.dto.UserUpdateRequest;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final TransportCryptoService transportCryptoService;
    private final SecurityUserDetailsService userDetailsService;

    public UserService(SysUserMapper userMapper, PasswordEncoder passwordEncoder,
                       AuditService auditService, JdbcTemplate jdbcTemplate,
                       TransportCryptoService transportCryptoService,
                       @Lazy SecurityUserDetailsService userDetailsService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.transportCryptoService = transportCryptoService;
        this.userDetailsService = userDetailsService;
    }

    public Page<SysUser> page(UserPrincipal operator, int page, int size, String keyword) {
        return page(operator, page, size, keyword, null);
    }

    public Page<SysUser> page(UserPrincipal operator, int page, int size, String keyword, Long orgId) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<>();
        if (!operator.isSystemAdmin()) {
            q.eq(SysUser::getOrgId, operator.getOrgId());
        } else if (orgId != null) {
            q.eq(SysUser::getOrgId, orgId);
        }
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(SysUser::getUsername, keyword).or().like(SysUser::getDisplayName, keyword));
        }
        q.orderByDesc(SysUser::getId);
        return userMapper.selectPage(new Page<>(page, size), q);
    }

    /** 按机构分页用户，并附带角色名称（组织机构树右侧面板） */
    public Page<UserListItem> pageWithRoles(UserPrincipal operator, int page, int size, String keyword, Long orgId) {
        if (orgId != null) {
            assertOrgAccess(operator, orgId);
        }
        Page<SysUser> raw = page(operator, page, size, keyword, orgId);
        Page<UserListItem> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<SysUser> records = raw.getRecords();
        if (records == null || records.isEmpty()) {
            out.setRecords(List.of());
            return out;
        }
        List<Long> userIds = records.stream().map(SysUser::getId).toList();
        String placeholders = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT ur.user_id AS user_id, ur.role_id AS role_id, r.role_name AS role_name "
                        + "FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                        + "WHERE ur.user_id IN (" + placeholders + ") ORDER BY r.id",
                userIds.toArray());
        Map<Long, List<Long>> roleIdsByUser = new HashMap<>();
        Map<Long, List<String>> roleNamesByUser = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long uid = ((Number) row.get("user_id")).longValue();
            Long rid = ((Number) row.get("role_id")).longValue();
            String rname = String.valueOf(row.get("role_name"));
            roleIdsByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(rid);
            roleNamesByUser.computeIfAbsent(uid, k -> new ArrayList<>()).add(rname);
        }
        List<UserListItem> items = new ArrayList<>();
        for (SysUser u : records) {
            UserListItem item = new UserListItem();
            item.setId(u.getId());
            item.setUsername(u.getUsername());
            item.setDisplayName(u.getDisplayName());
            item.setPhone(u.getPhone());
            item.setOrgId(u.getOrgId());
            item.setStatus(u.getStatus());
            item.setRoleIds(roleIdsByUser.getOrDefault(u.getId(), List.of()));
            item.setRoleNames(roleNamesByUser.getOrDefault(u.getId(), List.of()));
            items.add(item);
        }
        out.setRecords(items);
        return out;
    }

    @Transactional
    public Long create(UserPrincipal operator, UserCreateRequest req) {
        assertOrgAccess(operator, req.orgId());
        String plainPassword = transportCryptoService.decryptPassword(req.passwordTransport());
        validatePassword(plainPassword);
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.username())) > 0) {
            throw new BusinessException(400, "用户名已存在");
        }
        if (req.phone() == null || req.phone().isBlank()) {
            throw new BusinessException(400, "联系方式不能为空");
        }
        SysUser user = new SysUser();
        user.setUsername(req.username());
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        user.setDisplayName(req.displayName());
        user.setPhone(req.phone().trim());
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
        userDetailsService.evictPrincipalCache(user.getId());
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
        userDetailsService.evictPrincipalCache(id);
    }

    @Transactional
    public void resetPassword(UserPrincipal operator, Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        assertOrgAccess(operator, user.getOrgId());
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setPwdChangeFailCount(0);
        user.setPwdChangeLockedUntil(null);
        userMapper.updateById(user);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "USER_RESET_PASSWORD", "sys_user", String.valueOf(id), user.getUsername());
    }

    /** MS1：删除语义 = 禁用账号（对齐 TC-M211-001） */
    @Transactional
    public void disable(UserPrincipal operator, Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        assertOrgAccess(operator, user.getOrgId());
        if (operator.getUserId().equals(id)) {
            throw new BusinessException(400, "不能禁用当前登录账号");
        }
        user.setStatus(0);
        userMapper.updateById(user);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "USER_DISABLE", "sys_user", String.valueOf(id), user.getUsername());
    }

    /** 物理删除用户及其角色关联 */
    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        assertOrgAccess(operator, user.getOrgId());
        if (operator.getUserId().equals(id)) {
            throw new BusinessException(400, "不能删除当前登录账号");
        }
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", id);
        userMapper.deleteById(id);
        auditService.log(operator.getUserId(), operator.getUsername(), operator.getOrgId(),
                "USER_DELETE", "sys_user", String.valueOf(id), user.getUsername());
        userDetailsService.evictPrincipalCache(id);
    }

    public void assertOrgAccess(UserPrincipal operator, Long targetOrgId) {
        if (operator.isSystemAdmin()) {
            return;
        }
        if (!operator.getOrgId().equals(targetOrgId)) {
            throw new BusinessException(403, "无权操作其他机构用户");
        }
    }

    public void validatePassword(String password) {
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

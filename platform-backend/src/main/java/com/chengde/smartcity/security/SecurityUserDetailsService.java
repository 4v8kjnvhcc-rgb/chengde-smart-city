package com.chengde.smartcity.security;

import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SecurityUserDetailsService {

    private final SysUserMapper userMapper;

    public SecurityUserDetailsService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserPrincipal loadByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
        if (user == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new LockedException("账号已禁用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("账号已锁定，请稍后再试");
        }
        List<String> roles = userMapper.findRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.findPermissionsByUserId(user.getId());
        return new UserPrincipal(user.getId(), user.getUsername(), user.getOrgId(), user.getDisplayName(), roles, permissions);
    }

    public SysUser loadUserEntity(String username) {
        return userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username));
    }
}

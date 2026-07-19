package com.chengde.smartcity.auth;

import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.auth.dto.LoginRequest;
import com.chengde.smartcity.auth.dto.RefreshRequest;
import com.chengde.smartcity.auth.dto.TokenResponse;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.config.JwtProperties;
import com.chengde.smartcity.config.SecurityProperties;
import com.chengde.smartcity.security.JwtTokenProvider;
import com.chengde.smartcity.security.SecurityUserDetailsService;
import com.chengde.smartcity.security.SessionRedisService;
import com.chengde.smartcity.security.UserPrincipal;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import com.chengde.smartcity.system.service.SecurityConfigService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysOrgMapper orgMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;
    private final SessionRedisService sessionRedisService;
    private final SecurityUserDetailsService userDetailsService;
    private final SecurityConfigService securityConfigService;
    private final AuditService auditService;

    public AuthService(SysUserMapper userMapper, SysOrgMapper orgMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties, SecurityProperties securityProperties,
                       SessionRedisService sessionRedisService, SecurityUserDetailsService userDetailsService,
                       SecurityConfigService securityConfigService, AuditService auditService) {
        this.userMapper = userMapper;
        this.orgMapper = orgMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.securityProperties = securityProperties;
        this.sessionRedisService = sessionRedisService;
        this.userDetailsService = userDetailsService;
        this.securityConfigService = securityConfigService;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!sessionRedisService.checkRateLimit(request.username(), ip, 30)) {
            throw new BusinessException(429, "请求过于频繁");
        }
        SysUser user = userDetailsService.loadUserEntity(request.username());
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new LockedException("账号已禁用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new LockedException("账号已锁定，请稍后再试");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int fails = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            user.setFailedLoginCount(fails);
            if (fails >= securityProperties.loginMaxFailures()) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(securityProperties.loginLockMinutes()));
            }
            userMapper.updateById(user);
            auditService.log(user.getId(), user.getUsername(), user.getOrgId(), "LOGIN_FAIL", "auth", user.getUsername(), "密码错误");
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (securityConfigService.isTwoFactorEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new BusinessException(40101, "需要双因素验证码");
            }
            // MS1：占位校验，MS2 前对接短信/TOTP
            if (!"000000".equals(request.totpCode()) && !request.totpCode().equals(request.password().substring(0, 6))) {
                throw new BadCredentialsException("双因素验证码错误");
            }
        }
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Claims claims = tokenProvider.parse(request.refreshToken());
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BadCredentialsException("无效的 Refresh Token");
        }
        SysUser user = userDetailsService.loadUserEntity(claims.getSubject());
        if (user == null || user.getStatus() == 0) {
            throw new BadCredentialsException("用户不可用");
        }
        return issueTokens(user);
    }

    public void logout(UserPrincipal principal, String accessToken) {
        Claims claims = tokenProvider.parse(accessToken);
        sessionRedisService.logout(principal.getUserId(), claims.getId());
        auditService.log(principal.getUserId(), principal.getUsername(), principal.getOrgId(),
                "LOGOUT", "auth", principal.getUsername(), "用户登出");
    }

    private TokenResponse issueTokens(SysUser user) {
        String access = tokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getOrgId());
        String refresh = tokenProvider.createRefreshToken(user.getId(), user.getUsername());
        Claims accessClaims = tokenProvider.parse(access);
        sessionRedisService.bindActiveAccessToken(user.getId(), accessClaims.getId(), access);
        sessionRedisService.touchSession(user.getId());
        auditService.log(user.getId(), user.getUsername(), user.getOrgId(), "LOGIN", "auth", user.getUsername(), "登录成功");
        String orgName = null;
        if (user.getOrgId() != null) {
            SysOrg org = orgMapper.selectById(user.getOrgId());
            if (org != null) {
                orgName = org.getOrgName();
            }
        }
        return new TokenResponse(
                access,
                refresh,
                jwtProperties.accessTokenMinutes() * 60,
                new TokenResponse.UserInfo(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getOrgId(), orgName)
        );
    }
}

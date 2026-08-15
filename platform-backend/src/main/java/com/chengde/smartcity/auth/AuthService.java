package com.chengde.smartcity.auth;

import com.chengde.smartcity.audit.AuditService;
import com.chengde.smartcity.auth.dto.ChangePasswordRequest;
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
import com.chengde.smartcity.system.entity.SysAppearanceConfig;
import com.chengde.smartcity.system.entity.SysOrg;
import com.chengde.smartcity.system.entity.SysUser;
import com.chengde.smartcity.system.mapper.SysOrgMapper;
import com.chengde.smartcity.system.mapper.SysUserMapper;
import com.chengde.smartcity.system.service.AppearanceService;
import com.chengde.smartcity.system.service.CaptchaService;
import com.chengde.smartcity.system.service.SecurityConfigService;
import com.chengde.smartcity.system.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final AppearanceService appearanceService;
    private final CaptchaService captchaService;
    private final UserService userService;

    public AuthService(SysUserMapper userMapper, SysOrgMapper orgMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties, SecurityProperties securityProperties,
                       SessionRedisService sessionRedisService, SecurityUserDetailsService userDetailsService,
                       SecurityConfigService securityConfigService, AuditService auditService,
                       AppearanceService appearanceService, CaptchaService captchaService,
                       UserService userService) {
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
        this.appearanceService = appearanceService;
        this.captchaService = captchaService;
        this.userService = userService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        if (!sessionRedisService.checkRateLimit(request.username(), ip, 30)) {
            throw new BusinessException(429, "请求过于频繁");
        }
        SysAppearanceConfig appearance = appearanceService.requireRow();
        if (appearance.getLoginCaptchaEnabled() != null && appearance.getLoginCaptchaEnabled() == 1) {
            captchaService.verifyOrThrow(request.captchaId(), request.captchaCode());
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
        enforcePasswordAgeLock(user);

        int maxFails = securityConfigService.loginMaxFailures(securityProperties.loginMaxFailures());
        int lockMinutes = securityConfigService.loginLockMinutes(securityProperties.loginLockMinutes());

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            int fails = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
            user.setFailedLoginCount(fails);
            if (fails >= maxFails) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            }
            userMapper.updateById(user);
            auditService.log(user.getId(), user.getUsername(), user.getOrgId(), "LOGIN_FAIL", "auth", user.getUsername(), "密码错误");
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (securityConfigService.isTwoFactorEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new BusinessException(40101, "需要双因素验证码");
            }
            if (!"000000".equals(request.totpCode()) && !request.totpCode().equals(request.password().substring(0, Math.min(6, request.password().length())))) {
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

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(principal.getUserId());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (user.getPwdChangeLockedUntil() != null && user.getPwdChangeLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(423, "修改密码功能已锁定，请稍后再试");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            int fails = (user.getPwdChangeFailCount() == null ? 0 : user.getPwdChangeFailCount()) + 1;
            user.setPwdChangeFailCount(fails);
            int max = securityConfigService.pwdChangeMaxFailures();
            if (fails >= max) {
                user.setPwdChangeLockedUntil(LocalDateTime.now().plusMinutes(securityConfigService.pwdChangeLockMinutes()));
            }
            userMapper.updateById(user);
            auditService.log(user.getId(), user.getUsername(), user.getOrgId(),
                    "PWD_CHANGE_FAIL", "auth", user.getUsername(), "旧密码错误");
            throw new BusinessException(400, "旧密码错误");
        }
        userService.validatePassword(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPwdChangeFailCount(0);
        user.setPwdChangeLockedUntil(null);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);
        auditService.log(user.getId(), user.getUsername(), user.getOrgId(),
                "PWD_CHANGE", "auth", user.getUsername(), "用户修改密码");
    }

    private void enforcePasswordAgeLock(SysUser user) {
        int lockDays = securityConfigService.pwdExpireLockDays();
        if (lockDays <= 0) {
            return;
        }
        LocalDateTime changed = user.getPasswordChangedAt();
        if (changed == null) {
            changed = user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now();
        }
        long age = ChronoUnit.DAYS.between(changed.toLocalDate(), LocalDateTime.now().toLocalDate());
        if (age >= lockDays) {
            throw new LockedException("密码已超过最长使用天数，请联系管理员重置密码后再登录");
        }
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
        Boolean warn = null;
        Integer ageDays = null;
        String warnMsg = null;
        int warnDays = securityConfigService.pwdExpireWarnDays();
        LocalDateTime changed = user.getPasswordChangedAt();
        if (changed == null) {
            changed = user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now();
        }
        long age = ChronoUnit.DAYS.between(changed.toLocalDate(), LocalDateTime.now().toLocalDate());
        ageDays = (int) age;
        if (warnDays > 0 && age >= warnDays) {
            warn = true;
            warnMsg = "您的密码已使用 " + age + " 天，建议尽快修改密码（超过 "
                    + securityConfigService.pwdExpireLockDays() + " 天将无法登录）";
        }
        return new TokenResponse(
                access,
                refresh,
                jwtProperties.accessTokenMinutes() * 60,
                new TokenResponse.UserInfo(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getOrgId(), orgName),
                warn,
                ageDays,
                warnMsg
        );
    }
}

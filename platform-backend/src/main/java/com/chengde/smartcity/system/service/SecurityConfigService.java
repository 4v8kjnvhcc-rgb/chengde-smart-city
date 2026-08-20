package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.system.entity.SysSecurityConfig;
import com.chengde.smartcity.system.mapper.SysSecurityConfigMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SecurityConfigService {

    private final SysSecurityConfigMapper configMapper;

    public SecurityConfigService(SysSecurityConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    public Map<String, String> getAll() {
        List<SysSecurityConfig> list = configMapper.selectList(new LambdaQueryWrapper<>());
        Map<String, String> map = new HashMap<>();
        for (SysSecurityConfig c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }

    public void update(Map<String, String> configs) {
        for (Map.Entry<String, String> e : configs.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank()) {
                continue;
            }
            SysSecurityConfig row = configMapper.selectById(e.getKey());
            if (row != null) {
                row.setConfigValue(e.getValue());
                row.setUpdatedAt(LocalDateTime.now());
                configMapper.updateById(row);
            } else {
                SysSecurityConfig neu = new SysSecurityConfig();
                neu.setConfigKey(e.getKey());
                neu.setConfigValue(e.getValue());
                neu.setUpdatedAt(LocalDateTime.now());
                configMapper.insert(neu);
            }
        }
    }

    public String get(String key, String defaultValue) {
        SysSecurityConfig c = configMapper.selectById(key);
        if (c == null || c.getConfigValue() == null || c.getConfigValue().isBlank()) {
            return defaultValue;
        }
        return c.getConfigValue();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean isTwoFactorEnabled() {
        return "true".equalsIgnoreCase(get("two_factor_enabled", "false")) || isSecondFactorRequired();
    }

    /** 短信或 TOTP 任一开启时，登录需第二因子（验证码字段） */
    public boolean isSecondFactorRequired() {
        return isAuthMethodEnabled("sms", false) || isAuthMethodEnabled("totp", false);
    }

    public boolean isAuthMethodEnabled(String method, boolean defaultEnabled) {
        String key = "auth_method_" + method;
        String def = defaultEnabled ? "true" : "false";
        return "true".equalsIgnoreCase(get(key, def));
    }

    public String smsDemoCode() {
        return get("auth_sms_demo_code", "000000");
    }

    public boolean isAuditEnabled() {
        return !"false".equalsIgnoreCase(get("audit_enabled", "true"));
    }

    public int loginMaxFailures(int ymlFallback) {
        return getInt("login_max_failures", ymlFallback);
    }

    public int loginLockMinutes(int ymlFallback) {
        return getInt("login_lock_minutes", ymlFallback);
    }

    public int pwdChangeMaxFailures() {
        return getInt("pwd_change_max_failures", 5);
    }

    public int pwdChangeLockMinutes() {
        return getInt("pwd_change_lock_minutes", 60);
    }

    public int pwdExpireWarnDays() {
        return getInt("pwd_expire_warn_days", 5);
    }

    public int pwdExpireLockDays() {
        return getInt("pwd_expire_lock_days", 10);
    }
}

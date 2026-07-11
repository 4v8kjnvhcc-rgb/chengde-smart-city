package com.chengde.smartcity.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.system.entity.SysSecurityConfig;
import com.chengde.smartcity.system.mapper.SysSecurityConfigMapper;
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
            SysSecurityConfig row = configMapper.selectById(e.getKey());
            if (row != null) {
                row.setConfigValue(e.getValue());
                configMapper.updateById(row);
            }
        }
    }

    public boolean isTwoFactorEnabled() {
        SysSecurityConfig c = configMapper.selectById("two_factor_enabled");
        return c != null && "true".equalsIgnoreCase(c.getConfigValue());
    }

    public boolean isAuditEnabled() {
        SysSecurityConfig c = configMapper.selectById("audit_enabled");
        return c == null || "true".equalsIgnoreCase(c.getConfigValue());
    }
}

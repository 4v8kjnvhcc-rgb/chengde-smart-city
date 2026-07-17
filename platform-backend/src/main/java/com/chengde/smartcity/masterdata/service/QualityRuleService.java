package com.chengde.smartcity.masterdata.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chengde.smartcity.common.exception.BusinessException;
import com.chengde.smartcity.masterdata.entity.GovQualityRule;
import com.chengde.smartcity.masterdata.entity.GovQualityRuleConfig;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleConfigMapper;
import com.chengde.smartcity.masterdata.mapper.GovQualityRuleMapper;
import com.chengde.smartcity.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityRuleService {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleService.class);
    private static final Set<String> CHECK_TYPES = Set.of("NULL_CHECK", "UNIQUENESS", "ACCURACY", "RECORD_COUNT");

    private final GovQualityRuleMapper ruleMapper;
    private final GovQualityRuleConfigMapper configMapper;

    public QualityRuleService(GovQualityRuleMapper ruleMapper, GovQualityRuleConfigMapper configMapper) {
        this.ruleMapper = ruleMapper;
        this.configMapper = configMapper;
    }

    public List<Map<String, Object>> listWithConfig() {
        List<GovQualityRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .orderByDesc(GovQualityRule::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityRule rule : rules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rule.getId());
            row.put("ruleCode", rule.getRuleCode());
            row.put("ruleName", rule.getRuleName());
            row.put("ruleType", rule.getRuleType());
            row.put("orgId", rule.getOrgId());
            row.put("status", rule.getStatus());
            row.put("createdBy", rule.getCreatedBy());
            row.put("createdAt", rule.getCreatedAt());
            row.put("config", findConfig(rule.getId()));
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        GovQualityRule rule = requireRule(id);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("ruleCode", rule.getRuleCode());
        row.put("ruleName", rule.getRuleName());
        row.put("ruleType", rule.getRuleType());
        row.put("orgId", rule.getOrgId());
        row.put("status", rule.getStatus());
        row.put("createdBy", rule.getCreatedBy());
        row.put("createdAt", rule.getCreatedAt());
        row.put("config", findConfig(id));
        return row;
    }

    public GovQualityRuleConfig getConfig(Long ruleId) {
        requireRule(ruleId);
        GovQualityRuleConfig cfg = findConfig(ruleId);
        if (cfg == null) {
            throw new BusinessException(404, "规则尚未配置");
        }
        return cfg;
    }

    @Transactional
    public GovQualityRuleConfig saveConfig(UserPrincipal operator, Long ruleId, Map<String, Object> body) {
        requireRule(ruleId);
        String checkType = str(body.get("checkType"), null);
        if (checkType == null || !CHECK_TYPES.contains(checkType)) {
            throw new BusinessException(400, "checkType 须为 NULL_CHECK/UNIQUENESS/ACCURACY/RECORD_COUNT");
        }
        GovQualityRuleConfig cfg = findConfig(ruleId);
        boolean creating = cfg == null;
        if (creating) {
            cfg = new GovQualityRuleConfig();
            cfg.setRuleId(ruleId);
            cfg.setCreatedAt(LocalDateTime.now());
            if (operator != null) {
                cfg.setCreatedBy(operator.getUsername());
            }
        }
        cfg.setMetadataEntryCode(str(body.get("metadataEntryCode"), cfg.getMetadataEntryCode()));
        cfg.setCheckType(checkType);
        cfg.setTargetTable(str(body.get("targetTable"), null));
        cfg.setTargetColumn(str(body.get("targetColumn"), null));
        if (body.containsKey("configJson")) {
            Object cj = body.get("configJson");
            cfg.setConfigJson(cj == null ? null : String.valueOf(cj));
        }
        if (body.containsKey("threshold")) {
            cfg.setThreshold(toDecimal(body.get("threshold")));
        }
        cfg.setStatus(str(body.get("status"), cfg.getStatus() != null ? cfg.getStatus() : "ENABLED"));
        cfg.setUpdatedAt(LocalDateTime.now());
        if (creating) {
            configMapper.insert(cfg);
        } else {
            configMapper.updateById(cfg);
        }
        log.info("quality rule {} config saved checkType={}", ruleId, checkType);
        return cfg;
    }

    @Transactional
    public void delete(UserPrincipal operator, Long id) {
        requireRule(id);
        configMapper.delete(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, id));
        ruleMapper.deleteById(id);
    }

    private GovQualityRule requireRule(Long id) {
        GovQualityRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(404, "质量规则不存在: " + id);
        }
        return rule;
    }

    private GovQualityRuleConfig findConfig(Long ruleId) {
        return configMapper.selectOne(new LambdaQueryWrapper<GovQualityRuleConfig>()
                .eq(GovQualityRuleConfig::getRuleId, ruleId)
                .last("LIMIT 1"));
    }

    private static String str(Object v, String defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static BigDecimal toDecimal(Object v) {
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            throw new BusinessException(400, "threshold 格式无效");
        }
    }
}

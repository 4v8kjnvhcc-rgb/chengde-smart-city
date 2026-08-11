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
import java.util.UUID;
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
                .orderByAsc(GovQualityRule::getSortNo)
                .orderByAsc(GovQualityRule::getId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (GovQualityRule rule : rules) {
            out.add(toRow(rule));
        }
        return out;
    }

    public Map<String, Object> get(Long id) {
        return toRow(requireRule(id));
    }

    private Map<String, Object> toRow(GovQualityRule rule) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("ruleCode", rule.getRuleCode());
        row.put("ruleName", rule.getRuleName());
        row.put("ruleType", rule.getRuleType());
        row.put("sortNo", rule.getSortNo() != null ? rule.getSortNo() : 0);
        row.put("description", rule.getDescription());
        row.put("orgId", rule.getOrgId());
        row.put("status", rule.getStatus());
        row.put("createdBy", rule.getCreatedBy());
        row.put("createdAt", rule.getCreatedAt());
        row.put("config", findConfig(rule.getId()));
        return row;
    }

    @Transactional
    public Long create(UserPrincipal operator, Map<String, Object> body) {
        String name = str(body.get("ruleName"), null);
        if (name == null) {
            throw new BusinessException(400, "规则名称不能为空");
        }
        String code = str(body.get("ruleCode"), "QR_" + UUID.randomUUID().toString().substring(0, 8));
        ensureCodeUnique(code, null);
        GovQualityRule rule = new GovQualityRule();
        rule.setRuleCode(code);
        rule.setRuleName(name);
        rule.setRuleType(str(body.get("ruleType"), "COMPLETENESS"));
        rule.setSortNo(toInt(body.get("sortNo"), nextSortNo()));
        rule.setDescription(str(body.get("description"), null));
        if (operator != null) {
            rule.setOrgId(operator.getOrgId());
            rule.setCreatedBy(operator.getUsername());
        }
        rule.setStatus(str(body.get("status"), "ENABLED"));
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.insert(rule);
        log.info("quality rule created id={} code={}", rule.getId(), rule.getRuleCode());
        return rule.getId();
    }

    /** 对照旧页：支持修改排序 / 名称 / 描述（编码创建后不可改） */
    @Transactional
    public void update(UserPrincipal operator, Long id, Map<String, Object> body) {
        GovQualityRule rule = requireRule(id);
        String name = str(body.get("ruleName"), null);
        if (name == null) {
            throw new BusinessException(400, "规则名称不能为空");
        }
        rule.setRuleName(name);
        if (body.containsKey("ruleType")) {
            rule.setRuleType(str(body.get("ruleType"), rule.getRuleType()));
        }
        if (body.containsKey("sortNo")) {
            rule.setSortNo(toInt(body.get("sortNo"), rule.getSortNo() != null ? rule.getSortNo() : 0));
        }
        if (body.containsKey("description")) {
            rule.setDescription(str(body.get("description"), null));
        }
        if (body.containsKey("status")) {
            rule.setStatus(str(body.get("status"), rule.getStatus()));
        }
        rule.setUpdatedAt(LocalDateTime.now());
        ruleMapper.updateById(rule);
        log.info("quality rule updated id={} by={}", id, operator != null ? operator.getUsername() : null);
    }

    private int nextSortNo() {
        List<GovQualityRule> all = ruleMapper.selectList(new LambdaQueryWrapper<GovQualityRule>()
                .orderByDesc(GovQualityRule::getSortNo)
                .last("LIMIT 1"));
        if (all.isEmpty() || all.get(0).getSortNo() == null) {
            return 1;
        }
        return all.get(0).getSortNo() + 1;
    }

    private void ensureCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<GovQualityRule> q = new LambdaQueryWrapper<GovQualityRule>()
                .eq(GovQualityRule::getRuleCode, code);
        if (excludeId != null) {
            q.ne(GovQualityRule::getId, excludeId);
        }
        Long cnt = ruleMapper.selectCount(q);
        if (cnt != null && cnt > 0) {
            throw new BusinessException(400, "规则编码已存在: " + code);
        }
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
        if (cfg.getTargetTable() == null || cfg.getTargetTable().isBlank()) {
            throw new BusinessException(400, "目标表不能为空，请从平台分层库或登记源选择表");
        }
        if (!"RECORD_COUNT".equalsIgnoreCase(checkType)
                && (cfg.getTargetColumn() == null || cfg.getTargetColumn().isBlank())) {
            throw new BusinessException(400, "目标字段不能为空");
        }
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

    private static int toInt(Object v, int defaultVal) {
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            throw new BusinessException(400, "排序须为整数");
        }
    }
}
